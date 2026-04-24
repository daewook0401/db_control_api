package io.headerroute.routing.datasource;

public final class ProjectContext {

    private static final ThreadLocal<String> PROJECT_HOLDER = new ThreadLocal<>();

    private ProjectContext() {
    }

    public static void set(String project) {
        PROJECT_HOLDER.set(project);
    }

    public static String get() {
        return PROJECT_HOLDER.get();
    }

    public static void clear() {
        PROJECT_HOLDER.remove();
    }
}
