package net.kdt.pojavlaunch.modloaders;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

@Keep
public class FabricVersion {
    public String version;
    public boolean stable;

    public static class LoaderDescriptor extends FabricVersion {
        public FabricVersion loader;

        @NonNull
        @Override
        public String toString() {
            return loader != null ? loader.toString() : "null";
        }
    }

    @NonNull
    @Override
    public String toString() {
        return version;
    }
}
