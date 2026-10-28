package id.roleplay.hud;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Gang {
    public enum Type {
        GANG("Gang"), KELUARGA("Keluarga");
        private final String display;
        Type(String d) { this.display = d; }
        public String display() { return display; }
    }

    public final String name;
    public final Type type;
    public UUID leader;
    public double bank;
    public final Set<UUID> members = new LinkedHashSet<>();

    public Gang(String name, Type type, UUID leader) {
        this.name = name;
        this.type = type;
        this.leader = leader;
        this.members.add(leader);
    }
}
