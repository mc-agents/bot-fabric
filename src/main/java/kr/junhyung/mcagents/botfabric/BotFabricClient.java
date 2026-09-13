package kr.junhyung.mcagents.botfabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.client.gui.screens.PauseScreen;
import kr.junhyung.mcagents.botfabric.event.EventPump;
import kr.junhyung.mcagents.botfabric.rpc.Dispatcher;
import kr.junhyung.mcagents.botfabric.render.FrameBudget;
import kr.junhyung.mcagents.botfabric.render.RenderOptions;
import kr.junhyung.mcagents.botfabric.rpc.RpcClient;
import kr.junhyung.mcagents.botfabric.session.Session;
import kr.junhyung.mcagents.botfabric.task.TaskScheduler;
import kr.junhyung.mcagents.botfabric.tool.ToolRegistry;
import kr.junhyung.mcagents.botfabric.tools.ActivateBlockTool;
import kr.junhyung.mcagents.botfabric.tools.AttackEntityTool;
import kr.junhyung.mcagents.botfabric.tools.CanCraftTool;
import kr.junhyung.mcagents.botfabric.tools.ClickSlotTool;
import kr.junhyung.mcagents.botfabric.tools.CloseWindowTool;
import kr.junhyung.mcagents.botfabric.tools.CompleteCommandTool;
import kr.junhyung.mcagents.botfabric.tools.CraftItemTool;
import kr.junhyung.mcagents.botfabric.tools.DigBlockTool;
import kr.junhyung.mcagents.botfabric.tools.DragSlotsTool;
import kr.junhyung.mcagents.botfabric.tools.DropHeldItemTool;
import kr.junhyung.mcagents.botfabric.tools.EquipItemTool;
import kr.junhyung.mcagents.botfabric.tools.FindBlocksTool;
import kr.junhyung.mcagents.botfabric.tools.FishTool;
import kr.junhyung.mcagents.botfabric.tools.FindEntityTool;
import kr.junhyung.mcagents.botfabric.tools.FindItemTool;
import kr.junhyung.mcagents.botfabric.tools.FlyToTool;
import kr.junhyung.mcagents.botfabric.tools.GetBlockInfoTool;
import kr.junhyung.mcagents.botfabric.tools.GetTargetBlockTool;
import kr.junhyung.mcagents.botfabric.tools.GetRecipeTool;
import kr.junhyung.mcagents.botfabric.tools.GiveItemTool;
import kr.junhyung.mcagents.botfabric.tools.GetPlayerStateTool;
import kr.junhyung.mcagents.botfabric.tools.InteractEntityTool;
import kr.junhyung.mcagents.botfabric.tools.ListInventoryTool;
import kr.junhyung.mcagents.botfabric.tools.ListRecipesTool;
import kr.junhyung.mcagents.botfabric.tools.MoveInDirectionTool;
import kr.junhyung.mcagents.botfabric.tools.MoveToPositionTool;
import kr.junhyung.mcagents.botfabric.tools.OpenContainerTool;
import kr.junhyung.mcagents.botfabric.tools.OpenInventoryTool;
import kr.junhyung.mcagents.botfabric.tools.ReadBlockEntityTool;
import kr.junhyung.mcagents.botfabric.tools.ReadDisplaysTool;
import kr.junhyung.mcagents.botfabric.tools.GetPositionTool;
import kr.junhyung.mcagents.botfabric.tools.JumpTool;
import kr.junhyung.mcagents.botfabric.tools.LookAtTool;
import kr.junhyung.mcagents.botfabric.tools.GetWorldStateTool;
import kr.junhyung.mcagents.botfabric.tools.ReadAdvancementsTool;
import kr.junhyung.mcagents.botfabric.tools.ReadBookTool;
import kr.junhyung.mcagents.botfabric.tools.ReadContainerOptionsTool;
import kr.junhyung.mcagents.botfabric.tools.ReadBossBarsTool;
import kr.junhyung.mcagents.botfabric.tools.ReadPlayerListTool;
import kr.junhyung.mcagents.botfabric.tools.ReadScoreboardTool;
import kr.junhyung.mcagents.botfabric.tools.ReadStatsTool;
import kr.junhyung.mcagents.botfabric.tools.ReadTradesTool;
import kr.junhyung.mcagents.botfabric.tools.SetStanceTool;
import kr.junhyung.mcagents.botfabric.tools.SmeltItemTool;
import kr.junhyung.mcagents.botfabric.tools.PickBlockTool;
import kr.junhyung.mcagents.botfabric.tools.PlaceBlockTool;
import kr.junhyung.mcagents.botfabric.tools.ClickChatTool;
import kr.junhyung.mcagents.botfabric.tools.PressContainerButtonTool;
import kr.junhyung.mcagents.botfabric.tools.PressDialogButtonTool;
import kr.junhyung.mcagents.botfabric.tools.ReadWindowTool;
import kr.junhyung.mcagents.botfabric.tools.RespawnTool;
import kr.junhyung.mcagents.botfabric.tools.RunCommandTool;
import kr.junhyung.mcagents.botfabric.tools.ScreenshotTool;
import kr.junhyung.mcagents.botfabric.tools.SelectBundleItemTool;
import kr.junhyung.mcagents.botfabric.tools.SelectTradeTool;
import kr.junhyung.mcagents.botfabric.tools.SetBeaconEffectsTool;
import kr.junhyung.mcagents.botfabric.tools.SendChatTool;
import kr.junhyung.mcagents.botfabric.tools.SwitchServerTool;
import kr.junhyung.mcagents.botfabric.tools.TypeTextTool;
import kr.junhyung.mcagents.botfabric.tools.UseHeldItemTool;
import kr.junhyung.mcagents.botfabric.tools.WaitForWindowTool;
import kr.junhyung.mcagents.botfabric.tools.WaitTicksTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotFabricClient implements ClientModInitializer {
    public static final String AGENT_VERSION = versionOf("botfabric");
    public static final String MINECRAFT_VERSION = versionOf("minecraft");

    private static final Logger LOGGER = LoggerFactory.getLogger("botfabric");

    private static String versionOf(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    @Override
    public void onInitializeClient() {
        BotConfig config = BotConfig.fromEnvironment();
        if (!config.enabled()) {
            LOGGER.info("rpc disabled, running as a plain client");
            return;
        }

        TaskScheduler scheduler = new TaskScheduler();
        ToolRegistry tools = new ToolRegistry();

        RpcClient client = new RpcClient(config);
        Session session = new Session(client);

        /* Pod readiness is the link, and it is the only signal the operator has about one. */
        HealthServer.start(config.healthPort(), client::linked);
        EventPump events = new EventPump(client);
        FrameBudget.idle(config.frameRateLimit());

        tools.register(new GetPositionTool());
        tools.register(new GetPlayerStateTool());
        tools.register(new GetWorldStateTool());
        tools.register(new ReadScoreboardTool());
        tools.register(new ReadBossBarsTool());
        tools.register(new ReadPlayerListTool());
        tools.register(new ListInventoryTool());
        tools.register(new FindItemTool());
        tools.register(new GetBlockInfoTool());
        tools.register(new GetTargetBlockTool());
        tools.register(new FindBlocksTool());
        tools.register(new ReadBlockEntityTool());
        tools.register(new FindEntityTool());
        tools.register(new ReadDisplaysTool());
        tools.register(new CompleteCommandTool());
        tools.register(new LookAtTool());
        tools.register(new JumpTool());
        tools.register(new SetStanceTool());
        tools.register(new CloseWindowTool());
        tools.register(new SendChatTool());
        tools.register(new ReadWindowTool());
        tools.register(new ClickSlotTool());
        tools.register(new ReadTradesTool());
        tools.register(new SelectTradeTool());
        tools.register(new ReadContainerOptionsTool());
        tools.register(new PressContainerButtonTool());
        tools.register(new SetBeaconEffectsTool());
        tools.register(new SelectBundleItemTool());
        tools.register(new DragSlotsTool());
        tools.register(new DropHeldItemTool());
        tools.register(new OpenContainerTool(scheduler));
        tools.register(new OpenInventoryTool());
        tools.register(new WaitForWindowTool(scheduler));
        tools.register(new ActivateBlockTool(scheduler));
        tools.register(new PickBlockTool(scheduler));
        tools.register(new InteractEntityTool(scheduler));
        tools.register(new AttackEntityTool(scheduler));
        tools.register(new UseHeldItemTool(scheduler));
        tools.register(new MoveToPositionTool(scheduler));
        tools.register(new MoveInDirectionTool(scheduler));
        tools.register(new FlyToTool(scheduler));
        tools.register(new DigBlockTool(scheduler));
        tools.register(new PlaceBlockTool(scheduler));
        tools.register(new EquipItemTool());
        tools.register(new GiveItemTool());
        tools.register(new SmeltItemTool(scheduler));
        tools.register(new CraftItemTool(scheduler));
        tools.register(new FishTool(scheduler));
        tools.register(new CanCraftTool());
        tools.register(new GetRecipeTool());
        tools.register(new ListRecipesTool());
        tools.register(new SwitchServerTool(scheduler));
        tools.register(new WaitTicksTool(scheduler));
        tools.register(new ScreenshotTool(scheduler));
        tools.register(new ClickChatTool(scheduler));
        tools.register(new PressDialogButtonTool(scheduler));
        tools.register(new TypeTextTool());
        tools.register(new ReadBookTool());
        tools.register(new RunCommandTool());
        tools.register(new RespawnTool(scheduler));
        tools.register(new ReadAdvancementsTool());
        tools.register(new ReadStatsTool(scheduler));

        Dispatcher dispatcher = new Dispatcher(tools, scheduler, session, config.botName());

        events.register();
        boolean[] configured = new boolean[1];
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (!configured[0]) {
                configured[0] = true;
                minecraft.options.pauseOnLostFocus = false;
                minecraft.options.onboardAccessibility = false;
                /*
                The "Move with W, A, S and D" card sits in the corner of every screenshot a fresh
                client takes, and a screenshot is what this bot is for. It is the tutorial for a
                player who has never played, and there is no player.
                */
                minecraft.options.tutorialStep = TutorialSteps.NONE;
                RenderOptions.apply(minecraft.options, config.renderDistance());
                minecraft.options.save();

                /*
                Dialling here rather than at init, because the first tick is the first moment the
                client can act: everything before it is the resource load, and on a machine
                without a graphics card that is a minute and a half of building texture atlases in
                software. A bot that linked during it was asked to join a world it could not join
                yet -- the server logged it arriving and leaving in the same second -- and a pod
                that called itself ready was not.
                */
                LOGGER.info("dialling {}:{} as {}", config.host(), config.port(), config.botName());
                client.start(dispatcher);
                /*
                A server pushes its resource pack during configuration and, unless the answer is
                already "always", the client puts up a prompt and waits for somebody to click it.
                Nobody will: the bot then sits in configuration until join-server gives up with a
                spawn timeout, having never reached the world. A server that draws its interface
                with custom glyphs is also a server whose pack the bot has to have.
                */
            }
            /*
            The screens that open by themselves. The pause screen is the one that cost a real QA
            pass: a client in a window loses focus, Minecraft pauses, and the next screenshot is of
            the game menu rather than of the world. Under Xvfb there is no focus to lose, so CI
            cannot see it. pauseOnLostFocus is set below and does not help a screen already up.
            */
            if (Mc.screen() instanceof ChatScreen
                    || Mc.screen() instanceof AccessibilityOnboardingScreen
                    || Mc.screen() instanceof PauseScreen) {
                Mc.setScreen(null);
            }
            session.noticeDeath();
            scheduler.tick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> session.report("ready"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> session.report("disconnected"));

        LOGGER.info("{} tools ready; dialling {}:{} as {} once the client has loaded",
                tools.all().size(), config.host(), config.port(), config.botName());
    }
}
