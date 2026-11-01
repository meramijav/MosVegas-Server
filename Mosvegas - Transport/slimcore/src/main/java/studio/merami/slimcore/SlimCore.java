package studio.merami.slimcore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.merami.slimcore.baseclasses.ResourceId;

public final class SlimCore {
    public static final String MODID = "slimvehicle";
    public static final Logger LOGGER = LoggerFactory.getLogger("SlimCore");

    private SlimCore() {}

    public static ResourceId id(String path) {
        return new ResourceId(MODID, path);
    }
}
