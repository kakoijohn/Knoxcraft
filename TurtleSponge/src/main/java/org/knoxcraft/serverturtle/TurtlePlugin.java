package org.knoxcraft.serverturtle;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Stack;

import org.knoxcraft.hooks.KCTUploadHook;
import org.knoxcraft.jetty.server.JettyServer;
import org.knoxcraft.turtle3d.KCTScript;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent.Login;
import org.spongepowered.api.event.world.ChangeWorldWeatherEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;

import com.google.inject.Inject;


@Plugin(id = "kct",
    name = "TurtlePlugin",
    version = "0.2",
    description = "Knoxcraft Turtles Plugin for Minecraft")
public class TurtlePlugin {

    private static final String PLAYER_NAME = "playerName";
    private static final String SCRIPT_NAME = "scriptName";
    private static final String NUM_UNDO = "numUndo";
    private JettyServer jettyServer;
    @Inject
    private Logger log;
    private ScriptManager scripts;
    private HashMap<String, Stack<Stack<BlockRecord>>> undoBuffers;  //PlayerName->buffer
    @Inject
    private PluginContainer container;

    //////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public TurtlePlugin() {
        scripts = new ScriptManager();
        undoBuffers = new HashMap<String, Stack<Stack<BlockRecord>>>();
    }

    /**
     * Called when plugin is disabled.
     */
    @Listener
    public void onServerStop(GameStoppedServerEvent event) {
        if (jettyServer!=null) {
            jettyServer.shutdown();
        }
    }


    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        // Hey! The server has started!
        // Try instantiating your logger in here.
        // (There's a guide for that)
        log.info("Registering Knoxcraft Turtles plugin");

        //Canary.hooks().registerListener(this, this);
        // TODO: these seem to have no effect; figure out why!
        //boolean b1=Canary.getServer().consoleCommand("weather clear 1000000");
        //boolean b2=Canary.getServer().consoleCommand("gamerule doDaylightCycle false");
        //logger.trace(String.format("Did weather work? %s did daylight work? %s", b1, b2));

        try {
            jettyServer=new JettyServer();
            jettyServer.enable();
        } catch (Exception e){
            if (jettyServer!=null) {
                jettyServer.shutdown();
            }
            log.error("Cannot initialize TurtlePlugin: JettyServer failed to start", e);
        }

        //httpServer=new HttpUploadServer();
        //httpServer.enable(getLogman());
        log.info("Enabling "+container.getName() + " Version " + container.getVersion()); 
        log.info("Authored by "+container.getAuthors());
        //Canary.commands().registerCommands(this, this, false);

        // TODO fix this method
        //lookupFromDB();

        // set up commands
        setupCommands();
    }
    
    private void setupCommands() {
        // List all the scripts
        CommandSpec listScripts=CommandSpec.builder()
                .description(Text.of("List Knoxcraft Turtle Scripts"))
                .permission("")
                .arguments(
                        GenericArguments.optional(GenericArguments.string(Text.of(PLAYER_NAME))))
                .executor(new CommandExecutor() {
                    @Override
                    public CommandResult execute(CommandSource src,CommandContext args)
                    throws CommandException
                    {
                        log.debug(String.format("name of sender is: %s", src.getName().toLowerCase()));
                        src.sendMessage(Text.of(String.format("%s is listing turtle scripts (programs)", src.getName().toLowerCase())));
                        
                        Optional<String> optName=args.<String>getOne(PLAYER_NAME);
                        if (optName.isPresent()) {
                            String playerName=optName.get();
                            if (playerName.equalsIgnoreCase("all")) {
                                // List all scripts for all players
                                Map<String,Map<String,KCTScript>> allScriptMap=scripts.getAllScripts();
                                for (Entry<String,Map<String,KCTScript>> entry : allScriptMap.entrySet()) {
                                    playerName=entry.getKey();
                                    for (Entry<String,KCTScript> entry2 : entry.getValue().entrySet()) {
                                        src.sendMessage(Text.of(String.format("%s has the script %s", playerName, entry2.getKey())));
                                    }
                                }
                            } else {
                                // List only scripts for the given player
                                Map<String,KCTScript> map=scripts.getAllScriptsForPlayer(playerName);
                                for (Entry<String,KCTScript> entry : map.entrySet()) {
                                    src.sendMessage(Text.of(String.format("%s has script %s"), playerName, entry.getKey()));
                                }
                            }
                        } else {
                            Map<String,KCTScript> map=scripts.getAllScriptsForPlayer(src.getName().toLowerCase());
                            if (map==null) {
                                // hacky way to use a case-insensitive key in a map
                                // in the future, be sure to make all keys lower-case
                                map=scripts.getAllScriptsForPlayer(src.getName());
                            }
                            if (map==null) {
                                src.sendMessage(Text.of(String.format("We cannot find any scripts for %s", src.getName())));
                            }
                            for (Entry<String,KCTScript> entry : map.entrySet()) {
                                log.debug(String.format("%s => %s", entry.getKey(), entry.getValue().getLanguage()));
                                src.sendMessage(Text.of(String.format("%s => %s", entry.getKey(), entry.getValue().getLanguage())));
                            }
                        }
                        return CommandResult.success();
                    }
                }).build();
        Sponge.getCommandManager().register(this, listScripts, "scripts", "ls");
        
        // Invoke a script
        CommandSpec invokeScript=CommandSpec.builder()
                .description(Text.of("Invoke a Knoxcraft Turtle Script"))
                .permission("")
                .arguments(
                        GenericArguments.onlyOne(GenericArguments.string(Text.of(SCRIPT_NAME))),
                        GenericArguments.optional(GenericArguments.string(Text.of(PLAYER_NAME))))
                .executor(new CommandExecutor() {
                    @Override
                    public CommandResult execute(CommandSource src,CommandContext args)
                    throws CommandException
                    {
                        Optional<String> optScriptName=args.getOne(SCRIPT_NAME);
                        if (!optScriptName.isPresent()) {
                            src.sendMessage(Text.of("No script name provided! You must invoke a script by name"));
                        }

                        String scriptName=optScriptName.get();
                        String playerName = src.getName().toLowerCase();
                        
                        Optional<String> optPlayerName=args.getOne(PLAYER_NAME);
                        if (optPlayerName.isPresent()) {
                            playerName=optPlayerName.get();
                        }
                        
                        log.debug(String.format("%s invokes script %s from player %s", src.getName(), scriptName, playerName));

                        /*
                        //Create turtle
                        Turtle turtle = new Turtle();
                        turtle.turtleInit(sender);

                        //Get script from map
                        KCTScript script = null;
                        try  {     
                            log.trace(String.format("%s is looking for %s", playerName, scriptName));
                            for (String p : scripts.getAllScripts().keySet()) {
                                log.trace("Player name: "+p);
                                for (String s : scripts.getAllScriptsForPlayer(p).keySet()) {
                                    log.trace(String.format("Player name %s has script named %s", p, s));
                                }
                            }
                            script = scripts.getScript(playerName, scriptName);
                            if (script==null) {
                                log.warn(String.format("player %s cannot find script %s", playerName, scriptName));
                                src.sendMessage(Text.of(String.format("%s, you have no script named %s", playerName, scriptName)));
                                // FIXME Should be CommandResult.success()?
                                return CommandResult.empty();
                            }
                        }  catch (Exception e)  {
                            turtle.turtleConsole("Script failed to load!");
                            log.error("Script failed to load", e);
                            // FIXME Should be CommandResult.success()?
                            return CommandResult.empty();
                        }

                        //Execute script    
                        try  {
                            turtle.executeScript(script);
                        }  catch (Exception e)  {
                            turtle.turtleConsole("Script failed to execute!");
                            log.error("Script failed to execute", e);
                        }

                        //add script's blocks to undo buffer
                        try  {            
                            //create buffer if doesn't exist
                            if (!undoBuffers.containsKey(senderName)) {  
                                undoBuffers.put(senderName, new Stack<Stack<BlockRecord>>());
                            }    
                            //add to buffer
                            undoBuffers.get(senderName).push(turtle.getOldBlocks());            
                        }  catch (Exception e)  {
                            turtle.turtleConsole("Failed to add to undo buffer!");
                            log.error("Faile to add to undo buffer", e);
                        }
                        */
                        return CommandResult.success();
                    }
                }).build();
        Sponge.getCommandManager().register(this, invokeScript, "invoke", "in");
        
        CommandSpec undo=CommandSpec.builder()
                .description(Text.of("Undo the previous script"))
                .permission("")
                .arguments(GenericArguments.optional(GenericArguments.integer(Text.of(NUM_UNDO))))
                .executor(new CommandExecutor() {
                    @Override
                    public CommandResult execute(CommandSource src,CommandContext args)
                    throws CommandException
                    {
                        Optional<Integer> optNumUndo=args.getOne(NUM_UNDO);
                        int numUndo=1;
                        if (optNumUndo.isPresent()){
                            numUndo=optNumUndo.get();
                        }
                        log.debug("Undo invoked!");
                        
                        String senderName = src.getName().toLowerCase();

                        //sender has not executed any scripts
                        if (!undoBuffers.containsKey(senderName))  {  
                            src.sendMessage(Text.of("You have not executed any scripts to undo!"));
                        }  else {  //buffer exists
                            //get buffer
                            Stack<Stack<BlockRecord>> buffer = undoBuffers.get(senderName);

                            if (buffer.empty()){  //buffer empty
                                src.sendMessage(Text.of("There are no more scripts to undo!"));

                            }  else  {  //okay to undo last script executed

                                //get buffer
                                Stack<BlockRecord> blocks = buffer.pop();

                                //replace original blocks
                                while(!blocks.empty())  {
                                    // FIXME Currently, we just use the coordinates to replace the block with air, so it won't work 
                                    // underwater for example. 
                                    BlockRecord b=blocks.pop();
                                    /* TODO: make the undo part work
                                    World world = sender.asPlayer().getWorld();
                                    world.setBlockAt(b.getBlock().getPosition(), BlockType.Air);
                                    */
                                    //blocks.pop().revert();
                                }
                            }
                        }
                        
                        return CommandResult.success();
                    }
                }).build();
    }
    
    
    /**
     * Load the latest version of each script from the DB for each player on this world
     * TODO Check how Canary handles worlds; do we have only one XML file of scripts
     * for worlds and should we include the world name or world ID with the script?
     */
    private void lookupFromDB() {
        // FIXME translate to Sponge
//        KCTScriptAccess data=new KCTScriptAccess();
//        List<DataAccess> results=new LinkedList<DataAccess>();
//        Map<String,KCTScriptAccess> mostRecentScripts=new HashMap<String,KCTScriptAccess>();
//
//        try {
//            Map<String,Object> filters=new HashMap<String,Object>();
//            Database.get().loadAll(data, results, filters);
//            for (DataAccess d : results) {
//                KCTScriptAccess scriptAccess=(KCTScriptAccess)d;
//                // Figure out the most recent script for each player-scriptname combo
//                String key=scriptAccess.playerName+"-"+scriptAccess.scriptName;
//                if (!mostRecentScripts.containsKey(key)) {
//                    mostRecentScripts.put(key, scriptAccess);
//                } else {
//                    if (scriptAccess.timestamp > mostRecentScripts.get(key).timestamp) {
//                        mostRecentScripts.put(key,scriptAccess);
//                    }
//                }
//                log.trace(String.format("from DB: player %s has script %s at time %d%n", 
//                        scriptAccess.playerName, scriptAccess.scriptName, scriptAccess.timestamp));
//            }
//            TurtleCompiler turtleCompiler=new TurtleCompiler();
//            for (KCTScriptAccess scriptAccess : mostRecentScripts.values()) {
//                try {
//                    KCTScript script=turtleCompiler.parseFromJson(scriptAccess.json);
//                    script.setLanguage(scriptAccess.language);
//                    script.setScriptName(scriptAccess.scriptName);
//                    script.setSourceCode(scriptAccess.source);
//                    script.setPlayerName(scriptAccess.playerName);
//
//                    scripts.putScript(scriptAccess.playerName, script);
//                    log.info(String.format("Loaded script %s for player %s", 
//                            scriptAccess.scriptName, scriptAccess.playerName));
//                } catch (TurtleException e){
//                    log.error("Internal Server error", e);
//                }
//            }
//        } catch (DatabaseReadException e) {
//            log.error("cannot read DB", e);
//        }
    }

    //Listeners
    
    /**
     * @param loginEvent
     */
    @Listener
    public void onLogin(Login loginEvent) {
        // TODO: verify that this hook related to logging in
        // TODO prevent breaking blocks, by figuring out equivalent of setCanBuild(false);
        log.debug(String.format("player "+loginEvent.getTargetUser().getName()));
    }

    /**
     * TODO: Fix this hook. This doesn't seem to get called. I would like to shut rain off every time it starts raining.
     * 
     * @param hook
     */
    @Listener
    public void onWeatherChange(ChangeWorldWeatherEvent weatherChange) {
        // TODO turn off weather
        log.info(String.format("Weather listener called"));
    }

    /**
     * Listener called when scripts are uploaded to the server
     * 
     * @param event
     */
    @Listener
    public void uploadJSON(KCTUploadHook event) {
        log.trace("Hook called!");
        //add scripts to manager and db
        Collection<KCTScript> list = event.getScripts();
        for (KCTScript script : list)  {
            scripts.putScript(event.getPlayerName().toLowerCase(), script);

            // FIXME translate to Sponge
//            // This will create the table if it doesn't exist
//            // and then insert data for the script into a new row
//            KCTScriptAccess data=new KCTScriptAccess();
//            data.json=script.toJSONString();
//            data.source=script.getSourceCode();
//            data.playerName=event.getPlayerName();
//            data.scriptName=script.getScriptName();
//            data.language=script.getLanguage();
//            try {
//                Database.get().insert(data);
//            } catch (DatabaseWriteException e) {
//                // TODO how to log the full stack trace?
//                log.error(e.toString());
//            }
        }
    }
}