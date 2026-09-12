package kr.junhyung.mcagents.botfabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import kr.junhyung.mcagents.botfabric.event.EventPump;
import kr.junhyung.mcagents.botfabric.rpc.Dispatcher;
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
import kr.junhyung.mcagents.botfabric.tools.DropHeldItemTool;
import kr.junhyung.mcagents.botfabric.tools.EquipItemTool;
import kr.junhyung.mcagents.botfabric.tools.FindBlocksTool;
import kr.junhyung.mcagents.botfabric.tools.FishTool;
import kr.junhyung.mcagents.botfabric.tools.FindEntityTool;
import kr.junhyung.mcagents.botfabric.tools.FindItemTool;
import kr.junhyung.mcagents.botfabric.tools.FlyToTool;
import kr.junhyung.mcagents.botfabric.tools.GetBlockInfoTool;
import kr.junhyung.mcagents.botfabric.tools.GetRecipeTool;
import kr.junhyung.mcagents.botfabric.tools.GiveItemTool;
import kr.junhyung.mcagents.botfabric.tools.GetPlayerStateTool;
import kr.junhyung.mcagents.botfabric.tools.InteractEntityTool;
import kr.junhyung.mcagents.botfabric.tools.ListInventoryTool;
import kr.junhyung.mcagents.botfabric.tools.ListRecipesTool;
import kr.junhyung.mcagents.botfabric.tools.MoveInDirectionTool;
import kr.junhyung.mcagents.botfabric.tools.MoveToPositionTool;
import kr.junhyung.mcagents.botfabric.tools.OpenContainerTool;
import kr.junhyung.mcagents.botfabric.tools.ReadBlockEntityTool;
import kr.junhyung.mcagents.botfabric.tools.ReadDisplaysTool;
import kr.junhyung.mcagents.botfabric.tools.GetPositionTool;
import kr.junhyung.mcagents.botfabric.tools.JumpTool;
import kr.junhyung.mcagents.botfabric.tools.LookAtTool;
import kr.junhyung.mcagents.botfabric.tools.GetWorldStateTool;
import kr.junhyung.mcagents.botfabric.tools.ReadBossBarsTool;
import kr.junhyung.mcagents.botfabric.tools.ReadPlayerListTool;
import kr.junhyung.mcagents.botfabric.tools.ReadScoreboardTool;
import kr.junhyung.mcagents.botfabric.tools.SetStanceTool;
import kr.junhyung.mcagents.botfabric.tools.SmeltItemTool;
import kr.junhyung.mcagents.botfabric.tools.PlaceBlockTool;
import kr.junhyung.mcagents.botfabric.tools.PressDialogButtonTool;
import kr.junhyung.mcagents.botfabric.tools.ReadWindowTool;
import kr.junhyung.mcagents.botfabric.tools.RunCommandTool;
import kr.junhyung.mcagents.botfabric.tools.ScreenshotTool;
import kr.junhyung.mcagents.botfabric.tools.SendChatTool;
import kr.junhyung.mcagents.botfabric.tools.SwitchServerTool;
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

        tools.register(new GetPositionTool());
        tools.register(new GetPlayerStateTool());
        tools.register(new GetWorldStateTool());
        tools.register(new ReadScoreboardTool());
        tools.register(new ReadBossBarsTool());
        tools.register(new ReadPlayerListTool());
        tools.register(new ListInventoryTool());
        tools.register(new FindItemTool());
        tools.register(new GetBlockInfoTool());
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
        tools.register(new DropHeldItemTool());
        tools.register(new OpenContainerTool(scheduler));
        tools.register(new WaitForWindowTool(scheduler));
        tools.register(new ActivateBlockTool(scheduler));
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
        tools.register(new PressDialogButtonTool(scheduler));
        tools.register(new RunCommandTool());

        Dispatcher dispatcher = new Dispatcher(tools, scheduler, session, config.botName());

        events.register();
        boolean[] configured = new boolean[1];
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (!configured[0]) {
                configured[0] = true;
                minecraft.options.pauseOnLostFocus = false;
                minecraft.options.onboardAccessibility = false;
                minecraft.options.save();
            }
            if (minecraft.screen instanceof ChatScreen
                    || minecraft.screen instanceof AccessibilityOnboardingScreen) {
                minecraft.setScreen(null);
            }
            scheduler.tick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> session.report("ready"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> session.report("disconnected"));

        LOGGER.info("dialling {}:{} as {} with {} tools",
                config.host(), config.port(), config.botName(), tools.all().size());
        client.start(dispatcher);
    }
}
