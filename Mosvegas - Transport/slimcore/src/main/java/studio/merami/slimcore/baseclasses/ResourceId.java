package studio.merami.slimcore.baseclasses;

public record ResourceId(String namespace, String path) {
    public static final String DEFAULT_NAMESPACE = "minecraft";

    public ResourceId {
        if (namespace.isEmpty()) namespace = DEFAULT_NAMESPACE;
        for (int i = 0; i < namespace.length(); i++) {
            if (!namespaceChar(namespace.charAt(i))) throw new IllegalArgumentException("Non [a-z0-9_.-] character in namespace of location: " + namespace + ":" + path);
        }
        for (int i = 0; i < path.length(); i++) {
            if (!pathChar(path.charAt(i))) throw new IllegalArgumentException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
        }
    }

    public static ResourceId parse(String location) {
        int colon = location.indexOf(':');
        if (colon < 0) return new ResourceId(DEFAULT_NAMESPACE, location);
        return new ResourceId(location.substring(0, colon), location.substring(colon + 1));
    }

    private static boolean namespaceChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    private static boolean pathChar(char c) {
        return namespaceChar(c) || c == '/';
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
