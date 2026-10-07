package syrnic.genocide;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import syrnic.genocide.config.GenocideConfig;
import syrnic.genocide.event.ModEvents;

@Mod(GenocideMod.MODID)
public class GenocideMod {
    public static final String MODID = "genocide";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public GenocideMod(IEventBus modEventBus, ModContainer modContainer) {
        GenocideConfig.register(modContainer);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);

        NeoForge.EVENT_BUS.register(new ModEvents());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(GenocideConfig::reloadAll);
    }

    private void onConfigLoading(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == GenocideConfig.SPEC) {
            GenocideConfig.bakeFromSpec();
            GenocideConfig.loadBiomeConfigsFromDisk();
        }
    }

    private void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == GenocideConfig.SPEC) {
            GenocideConfig.bakeFromSpec();
            GenocideConfig.loadBiomeConfigsFromDisk();
        }
    }
}
