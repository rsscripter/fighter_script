package scripts;

import java.util.concurrent.Callable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import java.util.Timer;
import java.util.Random;

import org.powerbot.script.*;
import org.powerbot.script.rt4.ClientContext;
import org.powerbot.script.rt4.Game;
import org.powerbot.script.rt4.Item;
import org.powerbot.script.rt4.Npc;
import org.powerbot.script.rt4.*;

@Script.Manifest(description = "A antiban-implemented fighter script by hrev, accomodates close range-direct fighting" +
        "and safespotting (range/mage), barrier should be un-crossable; although plain safespots may also " +
        "work (but obviously not as efficiently). Also robust against ladder scenery where antiban might result in misclick "+
        "ladder climbs, it will climb back to the original floor, if this happens.", name = "master fighter")

public class masterFighter extends PollingScript<ClientContext>{

    long last_time;
    int last_exp =0;
    int currlevel;
    Tile starting_location;
    int heal_health = 55;
    boolean ranging;
    List<org.powerbot.script.rt4.Npc> npcs;
    List<org.powerbot.script.rt4.Item> items;
    Npc current_opponent = null;

    List<Npc> unreachable = new ArrayList<Npc>();

    JCheckBox far_close_option;
    List<JCheckBox> options;
    List<JCheckBox> food_options;
    JFrame food_frame;
    JFrame frame;
    boolean selected = false;
    boolean food_selected = false;

    Timer timer = new Timer();
    List<Integer> targets; // target ids
    List<Integer> food_ids;

    Random seed;

    @Override
    public void start() {
        seed = new Random();
        get_target_npcs();
        get_food_options();
        inventory();
        starting_location = ctx.players.local().tile();
        currlevel = cb_total();
        last_time = System.currentTimeMillis();
        last_exp = exp_total();
        ctx.input.speed(1);
    }

    @Override
    public void poll() {
        if (ranging) fc();
        else close_combat();
        random_mouse(12);
        if (random(0, 2000) == 1) overcome_levelup_stump();
        wait(random(200, 900));
    }

    private void random_mouse(int freq){
        if (random(0, freq) != 1) return;
        ctx.input.move(new Point(random(0, 400), random(0, 500)));
    }

    private int exp_total(){
        return ctx.skills.experience(Constants.SKILLS_ATTACK) +
                ctx.skills.experience(Constants.SKILLS_STRENGTH) +
                ctx.skills.experience(Constants.SKILLS_DEFENSE) +
                ctx.skills.experience(Constants.SKILLS_MAGIC) +
                ctx.skills.experience(Constants.SKILLS_RANGE) +
                ctx.skills.experience(Constants.SKILLS_HITPOINTS);
    }

    private int cb_total(){
        return ctx.skills.level(Constants.SKILLS_ATTACK) +
                ctx.skills.level(Constants.SKILLS_STRENGTH) +
                ctx.skills.level(Constants.SKILLS_DEFENSE) +
                ctx.skills.level(Constants.SKILLS_MAGIC) +
                ctx.skills.level(Constants.SKILLS_RANGE) +
                ctx.skills.level(Constants.SKILLS_HITPOINTS);
    }
    private void maintain (){
        overcome_levelup_stump();
        make_sure_correct_floor();
    }

    private void fc(){
        fight_range();
        relocate();
        fight_range();
        relocate();
        fight_range();
        relocate();
        eat();
        relocate_to_start(150);
        random_activity(120);
    }


    public void close_combat(){
        fight();fight();
        fight();
        eat();
        fight();fight();
        fight();
        random_activity(100);
        fight();fight();
        fight();
    }

    @Override
    public void stop(){
        ctx.controller.suspend();
    }

    private void random_activity(int frequency){
        if (random(0, frequency) == 1) random_tab();
    }

    private int random(int low, int high){
        return seed.nextInt(high-low) + low;
    }

    private void random_tab(){
        int r = random(0, 12);

        switch (r) {
            case 1:  inventory();
                break;
            case 2:  quests();
                break;
            case 3:  stats();
                break;
            case 4:  magic();
                break;
            case 5:  friends();
                break;
            case 6:  ignore();
                break;
            case 7:  clanchat();
                break;
            case 8:  emotes();
                break;
            case 9:  music();
                break;
            case 10: prayer();
                break;
            case 11: rotate_random(90,170);
                break;
            case 12: inspect();
                break;
            case 13: body();
                break;

        }

    }

    private void inventory() {ctx.game.tab(Game.Tab.INVENTORY);}
    private void quests() {ctx.game.tab(Game.Tab.QUESTS);}
    private void stats() {ctx.game.tab(Game.Tab.STATS);}
    private void magic() {ctx.game.tab(Game.Tab.MAGIC);}
    private void friends() {ctx.game.tab(Game.Tab.FRIENDS_LIST);}
    private void ignore() {ctx.game.tab(Game.Tab.IGNORED_LIST);}
    private void clanchat() {ctx.game.tab(Game.Tab.CLAN_CHAT);}
    private void emotes() {ctx.game.tab(Game.Tab.EMOTES);}
    private void music() {ctx.game.tab(Game.Tab.MUSIC);}
    private void prayer() {ctx.game.tab(Game.Tab.PRAYER);}
    private void body() {ctx.game.tab(Game.Tab.EQUIPMENT);}
    private void rotate_random(int min, int max){ ctx.camera.angle(random(min, max)); }
    private void inspect(){
        Item i = ctx.inventory.itemAt(random(0, 27));
        if (i != null) i.hover();
    }

    private void overcome_levelup_stump(){
        if (ctx.players.local().inMotion()) return;
        if (ctx.npcs.select(new Filter<Npc>() {
            @Override
            public boolean accept(Npc npc) {
                return npc.interacting() == ctx.players.local();
            }
        }).size() == 0) {
            if (ranging) attack_range();
            else attack();
        }
    }

    private void eat(){
        if (food_available() == 0 &&
                ctx.players.local().healthPercent() < 70) ctx.controller.suspend();

        if (ctx.players.local().healthPercent() <= heal_health){
            inventory();
            final int chp = ctx.players.local().healthPercent();
            if (food_available() > 0) {
                ctx.inventory.select().id(primitive_int_array(food_ids)).poll().interact("Eat");
                heal_health = random(55, 75);

                Condition.wait(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return ctx.players.local().healthPercent() != chp;
                    }
                }, 200, 10);

                wait(1000);
            }
        }
    }

    private void fight_range(){

        if (ctx.players.local().inCombat()) return;

        else if (ctx.players.local().interacting() == null ||
                ctx.players.local().interacting().healthPercent() <= 0) attack_range();
    }

    private void make_sure_correct_floor(){
        if (ctx.npcs.size() == 0 && !ctx.players.local().inCombat() && !ctx.players.local().inMotion()) {
            climb_down();
            wait(random(1200, 3321));
        }
    }

    private void fight(){
            if (random(0, 50) == 1) make_sure_correct_floor();
            if (((current_opponent != null && current_opponent.healthPercent() <= 0)) ||
        (ctx.players.local().interacting() != null && ctx.players.local().interacting().healthPercent() <= 0)) {
                int r = random(0, 5);
                if (r == 2) wait(random(700, 1500));
                attack();
                return;
            }

        if (ctx.players.local().interacting() != null && ctx.players.local().interacting().combatLevel() > 0) return;
        if (current_opponent != null && current_opponent.interacting() == ctx.players.local()) return;
        if (ctx.players.local().inMotion()) return;
        if (ctx.players.local().inCombat()) return;
        if (ctx.players.local().interacting().healthPercent() > 0) return;
        attack();
    }

    private void climb_down(){


        GameObject ladder = ctx.objects.select(new Filter<GameObject>() {
            @Override
            public boolean accept(GameObject gameObject) {
                return gameObject.name().toLowerCase().contains("adde");
            }
        }).nearest().poll();
        if (containsAction("Climb-down", ladder)) ladder.interact("Climb-down");
    }

    private int food_available(){
        return ctx.inventory.select().id(primitive_int_array(food_ids)).count();
    }

    private int[] primitive_int_array(List<Integer> l){
        int[] a = new int[l.size()];
        for (int i = 0; i < l.size(); i++){
            a[i] = l.get(i);
        }
        return a;
    }
    private int s(){
        int r = random(0,1);
        if (r == 0) return -1;
        return 1;
    }

    private void hit(){
        current_opponent.interact("Attack");

        Condition.wait(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return ctx.players.local().inCombat() || current_opponent.healthPercent() == 0;
            }
        } ,20, 50);
        wait(1000);
    }

    private void relocate(){
        if (ctx.players.local().tile().distanceTo(starting_location) < 2) return;
        ctx.movement.step(new Tile(
                starting_location.x() + s(),
                starting_location.y() + s()
        ) );
    }

    private void relocate_to_start(int freq){
        if (random(0, freq) == 1) {
            ctx.movement.step(new Tile(
                    starting_location.x() + s(),
                    starting_location.y()
            ));
        }
    }

    public boolean containsAction(String action, GameObject sceneObject){
        return sceneObject==null ? false : Arrays.asList(sceneObject.actions()).contains(action);
    }

    private void attack_range(){
        // get closest opponent
        if (unreachable.size() > targets.size()) unreachable.clear();
        current_opponent = ctx.npcs.select().id(primitive_int_array(targets)).select(
                new Filter<Npc>() {
                    @Override
                    public boolean accept(Npc npc) {

                        return !npc.inCombat();
                    }
                }
        ).nearest().poll();

        // reachable, and viewable
        if(current_opponent.inViewport() && current_opponent.tile().distanceTo(ctx.players.local()) < 8) {
            hit();
            return;
        }
    }

    private void attack(){
        //count available targets, if none, climb down
        if (ctx.npcs.size() == 0 && ctx.groundItems.size() == 0) {
            climb_down();
            wait(1000);
        }

        // get closest opponent
        current_opponent = ctx.npcs.select().id(primitive_int_array(targets)).select(
                new Filter<Npc>() {
                    @Override
                    public boolean accept(Npc npc) {
                        if (unreachable.size() > targets.size()) unreachable.clear();
                        return !npc.inCombat() && npc != current_opponent && !unreachable.contains(npc);
                    }
                }
        ).nearest().poll();

        // get location
        Tile loc = current_opponent.tile();

            // if unreachable add it to non attack list
            if (!loc.matrix(ctx).reachable()) {
                unreachable.add(current_opponent);
                return;
            }

        // reachable, and viewable
        if(current_opponent.inViewport()) {
            hit();
            return;
        }

        //off screen but hittable
        Tile n = new Tile(loc.x()+ s()*random(0, 2), loc.y()+ s()*random(0, 3));
        ctx.movement.step(n);
    }
    private void wait(int ms){

        try { TimeUnit.MILLISECONDS.sleep(ms/2); }
        catch (Exception e) { }

        try { ctx.controller.wait(ms/2); }
        catch (Exception e) { }
    }

    private void get_food_options() {
        Set<String> names =
                new HashSet<String>();

        items = new ArrayList<org.powerbot.script.rt4.Item>();

        for(int i = 0; i < 28; i++) {
            int isize = names.size();
            names.add( ctx.inventory.itemAt(i).name());
            if (names.size() > isize && ctx.inventory.itemAt(i)!= null
                    && ctx.inventory.itemAt(i).name().length() > 2) items.add(ctx.inventory.itemAt(i));
        }

        food_options = new ArrayList<JCheckBox>();

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setMinimumSize(new Dimension(300, 300));
        panel.setMaximumSize(new Dimension(300, 300));

        // make actual popup

        food_frame = new JFrame("Food Select Menu");
        food_frame.setMinimumSize(new Dimension(300, 300));
        food_frame.setMaximumSize(new Dimension(300, 300));

        GridBagConstraints constraints = new GridBagConstraints();

        for(int i = 0; i < items.size(); i++){
            food_options.add(new JCheckBox(items.get(i).name()));
            constraints.gridy++;
            panel.add(food_options.get(i), constraints);
        }

        if (items.size() == 0) {
            panel.add( new JLabel("No food detected, click to continue."));
        }

        final JButton submit2 = new JButton("Ok");
        constraints.gridy ++;

        food_ids = new ArrayList<Integer>();
        submit2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for(int i = 0; i < items.size(); i++){
                    if (food_options.get(i).isSelected()) {
                        food_ids.add(items.get(i).id());
                    }
                }
                submit2.removeAll();
                food_frame.dispose();
                food_selected = true;
            }
        });

        panel.add(submit2, constraints);
        food_frame.add(panel);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBounds(50, 50, 100, 100);
        food_frame.add(scrollPane);
        food_frame.setVisible(true);

        while (!food_selected){
            wait(200);
        }

        food_selected = false;
    }

    private void get_target_npcs(){
        far_close_option = new JCheckBox("Safe Spot Mode");

        List<Npc> all_npcs = ctx.npcs.get();
        npcs = new ArrayList<Npc>();
        Set<Integer> ids = new HashSet<Integer>();

        //filter npcs to include only monsters
        for(int i = 0; i < all_npcs.size(); i++){
            if (all_npcs.get(i).combatLevel() > 0) {
                int isize = ids.size();
                ids.add(all_npcs.get(i).id());
                if (ids.size() > isize) npcs.add(all_npcs.get(i));
            }
        }

        options = new ArrayList<JCheckBox>();
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setMinimumSize(new Dimension(300, 300));
        panel.setMaximumSize(new Dimension(300, 300));

        // make actual popup

        frame = new JFrame("NPC Select Menu");
        frame.setMinimumSize(new Dimension(300, 300));
        frame.setMaximumSize(new Dimension(300, 300));

        GridBagConstraints constraints = new GridBagConstraints();

        if (npcs.size() == 0) {
            panel.add( new JLabel("No targets detected...move and try again."));
        }

        panel.add(far_close_option);
        constraints.gridy+= 2;

        for(int i = 0; i < npcs.size(); i++){
            String option =
                    String.valueOf(npcs.get(i).name()) + " - " +
                            String.valueOf(npcs.get(i).combatLevel()) + " - " +
                            String.valueOf(npcs.get(i).id());
            options.add(new JCheckBox(option));

            // add the checkbox to the panel
            constraints.gridy++;
            panel.add(  options.get(i), constraints);
        }

        final JButton submit = new JButton("Ok");
        constraints.gridy ++;

        targets = new ArrayList<Integer>();

        submit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ranging = far_close_option.isSelected();
                int s = 0;
                for(int i = 0; i < npcs.size(); i++){
                    if (options.get(i).isSelected()) {
                        targets.add( npcs.get(i).id());
                        s++;
                    }
                }
                submit.removeAll();
                frame.dispose();
                if (s == 0) { ctx.controller.suspend();}
                selected = true;
            }
        });


        panel.add(submit, constraints);
        frame.add(panel);

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBounds(50, 50, 100, 100);
        frame.add(scrollPane);

        frame.setVisible(true);

        while (!selected){
           wait(200);
        }
        selected = false;
    }
}
