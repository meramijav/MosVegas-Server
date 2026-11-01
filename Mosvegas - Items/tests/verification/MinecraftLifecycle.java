import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;

/** Requests Minecraft's own save-and-exit path without input or window automation. */
public final class MinecraftLifecycle {
    public static void agentmain(String unused, Instrumentation instrumentation) throws Exception {
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            if (!type.getName().equals("net.minecraft.client.MinecraftClient")) continue;
            Object client = type.getMethod("getInstance").invoke(null);
            type.getMethod("scheduleStop").invoke(client);
            System.out.println("MosVegas: requested graceful shutdown; Minecraft will save its world.");
            return;
        }
        throw new IllegalStateException("Target process is not the development Minecraft client");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected Minecraft PID and agent JAR path");
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1]); }
        finally { vm.detach(); }
        System.out.println("Requested clean Minecraft shutdown for PID " + args[0]);
    }
}
