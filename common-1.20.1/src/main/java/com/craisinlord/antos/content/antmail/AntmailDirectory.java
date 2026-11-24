package com.craisinlord.antos.content.antmail;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AntmailDirectory {
    public static final int MAX_CONTACTS = 256;
    public static final int MAX_LISTS = 64;
    public static final int PAGE_SIZE = 12;
    private static final Gson JSON = new Gson();
    private final Map<UUID, Map<UUID, String>> contacts = new LinkedHashMap<>();
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private long revision;
    public interface Accounts {
        UUID resolve(String reference);
        String address(UUID id);
        default List<Person> search(String query) {
            UUID id = resolve(query);
            return id == null ? List.of() : List.of(new Person(id, address(id), ""));
        }
    }
    public static final int MAX_DECLINED = 256;
    public static final String OWNER = "owner";
    public static final String MANAGER = "manager";
    public static final String MEMBER = "member";
    


 
    public record View(UUID id, String kind, String name, UUID owner, List<UUID> managers, List<UUID> members, long revision, int memberCount, String role) {
        public boolean canManage() { return OWNER.equals(role) || MANAGER.equals(role); }
        public boolean isOwner() { return OWNER.equals(role); }
    }
    public record Person(UUID id, String address, String alias) { }
    public record Page(String mode, String query, int page, boolean more, long revision, List<Person> people, List<View> lists) { }
    public static final String CONVERSATION_GROUP = "group";
    public static final String CONVERSATION_TEAM = "team";
    


 
    public record Audience(List<UUID> accounts, UUID conversation, Map<UUID, UUID> epochs, String conversationKind, String conversationName) {
        public Audience(List<UUID> accounts, UUID conversation, Map<UUID, UUID> epochs) { this(accounts, conversation, epochs, conversation == null ? null : CONVERSATION_GROUP, null); }
    }
     
    public record TeamView(UUID id, String ownerName, UUID owner, Map<UUID, UUID> epochs) {
        public boolean member(UUID account) { return account != null && epochs.containsKey(account); }
    }
    public interface Teams {
        Teams NONE = new Teams() {
            public UUID teamOf(UUID account) { return null; }
            public TeamView team(UUID id) { return null; }
        };
        UUID teamOf(UUID account);
        TeamView team(UUID id);
    }
    private static final class Entry {
        UUID id;
        String kind;
        String name;
        UUID owner;
        Set<UUID> managers = new LinkedHashSet<>();
        Map<UUID, UUID> members = new LinkedHashMap<>();
         
        Set<UUID> declined = new LinkedHashSet<>();
        long revision;
        String role(UUID actor) {
            if (owner.equals(actor)) return OWNER;
            if (managers.contains(actor)) return MANAGER;
            return members.containsKey(actor) ? MEMBER : "";
        }
        View view(UUID actor) {
            String role = role(actor);
            boolean roster = !kind.equals("mailing") || owner.equals(actor) || managers.contains(actor);
            return new View(id, kind, name, owner, roster ? List.copyOf(managers) : List.of(), roster ? List.copyOf(members.keySet()) : List.of(), revision, members.size(), role);
        }
        View summary(UUID actor) { return new View(id, kind, name, owner, List.of(), List.of(), revision, members.size(), role(actor)); }
        boolean manager(UUID actor) { return owner.equals(actor) || managers.contains(actor); }
        boolean visible(UUID actor) { return owner.equals(actor) || !kind.equals("personal") && (manager(actor) || members.containsKey(actor)); }
    }
    public long revision() { return revision; }
    public String label(UUID actor, AntmailRecipients.Selection selection, Accounts accounts) { return label(actor, selection, accounts, Teams.NONE); }
    public String label(UUID actor, AntmailRecipients.Selection selection, Accounts accounts, Teams teams) {
        if (selection.kind() == AntmailRecipients.Kind.TEAM) {
            try {
                TeamView team = teams.team(UUID.fromString(selection.reference()));
                return team != null && team.member(actor) ? team.ownerName() : "";
            } catch (IllegalArgumentException exception) { return ""; }
        }
        if (selection.kind() == AntmailRecipients.Kind.ACCOUNT) {
            UUID account = accounts.resolve(selection.reference());
            if (account == null) return "";
            String alias = contacts.getOrDefault(actor, Map.of()).getOrDefault(account, "");
            String address = accounts.address(account);
            return alias.isBlank() ? address == null ? "" : address : alias;
        }
        try { return view(actor, UUID.fromString(selection.reference())).name(); } catch (IllegalArgumentException exception) { return ""; }
    }
    private static String name(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || value.length() > 48 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("invalid_name");
        return value;
    }
    public void contact(UUID actor, UUID target, String alias, boolean remove, Accounts accounts) {
        requireAccount(target, accounts);
        if (alias == null || alias.length() > 48 || alias.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("invalid_alias");
        Map<UUID, String> book = contacts.computeIfAbsent(actor, ignored -> new LinkedHashMap<>());
        if (remove) book.remove(target);
        else {
            if (!book.containsKey(target) && book.size() >= MAX_CONTACTS) throw new IllegalArgumentException("contact_limit");
            book.put(target, alias.trim());
        }
        revision++;
    }
    public View create(UUID actor, UUID id, String kind, String title) {
        if (!List.of("personal", "mailing", "conversation").contains(kind)) throw new IllegalArgumentException("invalid_kind");
        Entry existing = entries.get(id);
        if (existing != null) {
            if (!existing.owner.equals(actor)) throw new IllegalArgumentException("forbidden");
            return existing.view(actor);
        }
        if (ownedCount(actor) >= MAX_LISTS) throw new IllegalArgumentException("list_limit");
        Entry entry = new Entry();
        entry.id = id;
        entry.owner = actor;
        entry.kind = kind;
        entry.name = name(title);
        if (!kind.equals("personal")) entry.members.put(actor, UUID.randomUUID());
        entry.revision = ++revision;
        entries.put(id, entry);
        return entry.view(actor);
    }
    


 
    public View leave(UUID actor, UUID id) {
        Entry entry = entries.get(id);
        if (entry == null || entry.kind.equals("personal") || !entry.members.containsKey(actor)) throw new IllegalArgumentException("not_member");
        if (entry.owner.equals(actor)) throw new IllegalArgumentException("owner_cannot_leave");
        entry.members.remove(actor);
        entry.managers.remove(actor);
        entry.declined.remove(actor);
        if (entry.declined.size() >= MAX_DECLINED) entry.declined.remove(entry.declined.iterator().next());
        entry.declined.add(actor);
        entry.revision = ++revision;
        return entry.view(actor);
    }
    public View change(UUID actor, UUID id, long expectedRevision, String operation, String value, Accounts accounts) {
        if (operation.equals("leave")) return leave(actor, id);
        Entry entry = entries.get(id);
        if (entry == null || !entry.manager(actor)) throw new IllegalArgumentException("forbidden");
        if (entry.revision != expectedRevision) throw new IllegalArgumentException("revision_conflict");
        switch (operation) {
            case "rename" -> entry.name = name(value);
            case "delete" -> {
                if (!entry.owner.equals(actor)) throw new IllegalArgumentException("owner_required");
                entries.remove(id);
            }
            case "add", "remove", "promote", "demote", "transfer" -> {
                UUID target = accounts.resolve(value);
                requireAccount(target, accounts);
                if (operation.equals("add")) {
                    if (!entry.members.containsKey(target) && entry.declined.contains(target)) throw new IllegalArgumentException("member_declined");
                    if (!entry.members.containsKey(target) && entry.members.size() >= AntmailRecipients.MAX_RECIPIENTS) throw new IllegalArgumentException("member_limit");
                    entry.members.putIfAbsent(target, UUID.randomUUID());
                } else if (operation.equals("remove")) {
                    if (!entry.kind.equals("personal") && target.equals(entry.owner)) throw new IllegalArgumentException("owner_required");
                    if (entry.managers.contains(target) && !entry.owner.equals(actor)) throw new IllegalArgumentException("owner_required");
                    entry.members.remove(target);
                    entry.managers.remove(target);
                } else {
                    if (!entry.owner.equals(actor) || entry.kind.equals("personal")) throw new IllegalArgumentException("owner_required");
                    if (!entry.members.containsKey(target)) throw new IllegalArgumentException("member_required");
                    if (operation.equals("promote")) entry.managers.add(target);
                    if (operation.equals("demote")) entry.managers.remove(target);
                    if (operation.equals("transfer")) {
                        if (!target.equals(entry.owner) && ownedCount(target) >= MAX_LISTS) throw new IllegalArgumentException("list_limit");
                        entry.managers.add(actor); entry.owner = target;
                    }
                }
            }
            default -> throw new IllegalArgumentException("invalid_operation");
        }
        entry.revision = ++revision;
        return entry.view(actor);
    }
    public View view(UUID actor, UUID id) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.visible(actor)) throw new IllegalArgumentException("forbidden");
        return entry.view(actor);
    }
    public Audience expand(UUID actor, List<AntmailRecipients.Selection> selections, Accounts accounts) { return expand(actor, selections, accounts, Teams.NONE); }
    public Audience expand(UUID actor, List<AntmailRecipients.Selection> selections, Accounts accounts, Teams teams) {
        if (selections.isEmpty() || selections.size() > AntmailRecipients.MAX_RECIPIENTS) throw new IllegalArgumentException("recipient_limit");
        Set<UUID> audience = new LinkedHashSet<>();
        Map<UUID, UUID> epochs = new LinkedHashMap<>();
        UUID conversation = null;
        String conversationKind = null;
        String conversationName = null;
        for (var selection : selections) {
            if (selection.kind() == AntmailRecipients.Kind.TEAM) {
                TeamView team = teams.team(UUID.fromString(selection.reference()));
                if (team == null) throw new IllegalArgumentException("team_not_found");
                if (!team.member(actor)) throw new IllegalArgumentException("forbidden");
                if (selections.size() != 1) throw new IllegalArgumentException("conversation_only");
                if (team.epochs().size() > AntmailRecipients.MAX_RECIPIENTS) throw new IllegalArgumentException("team_too_large");
                conversation = team.id();
                conversationKind = CONVERSATION_TEAM;
                conversationName = team.ownerName();
                epochs.putAll(team.epochs());
                audience.addAll(team.epochs().keySet());
            } else if (selection.kind() == AntmailRecipients.Kind.ACCOUNT) {
                UUID target = accounts.resolve(selection.reference());
                requireAccount(target, accounts);
                audience.add(target);
            } else {
                Entry entry = entries.get(UUID.fromString(selection.reference()));
                if (entry == null || !entry.kind.equals(selection.kind().name().toLowerCase(java.util.Locale.ROOT))) throw new IllegalArgumentException("list_not_found");
                if (entry.kind.equals("personal") && !entry.owner.equals(actor)
                        || entry.kind.equals("mailing") && !entry.manager(actor)
                        || entry.kind.equals("conversation") && !entry.members.containsKey(actor)) throw new IllegalArgumentException("forbidden");
                if (entry.kind.equals("conversation")) {
                    if (selections.size() != 1) throw new IllegalArgumentException("conversation_only");
                    conversation = entry.id;
                    conversationKind = CONVERSATION_GROUP;
                    conversationName = entry.name;
                    epochs.putAll(entry.members);
                }
                audience.addAll(entry.members.keySet());
            }
            if (audience.size() > AntmailRecipients.MAX_RECIPIENTS) throw new IllegalArgumentException("recipient_limit");
        }
        if (conversation != null) audience.remove(actor);
        if (audience.isEmpty() && conversation == null) throw new IllegalArgumentException("empty_audience");
        for (UUID target : audience) requireAccount(target, accounts);
        return new Audience(List.copyOf(audience), conversation, Map.copyOf(epochs), conversationKind, conversationName);
    }
    public boolean retryAllowed(UUID conversation, UUID account, UUID epoch) { return retryAllowed(CONVERSATION_GROUP, conversation, account, epoch, Teams.NONE); }
     
    public boolean retryAllowed(String kind, UUID conversation, UUID account, UUID epoch, Teams teams) {
        if (conversation == null) return true;
        if (epoch == null) return false;
        if (CONVERSATION_TEAM.equals(kind)) {
            TeamView team = teams.team(conversation);
            return team != null && epoch.equals(team.epochs().get(account));
        }
        Entry entry = entries.get(conversation);
        return entry != null && epoch.equals(entry.members.get(account));
    }
     
    public boolean currentConversation(UUID actor, UUID id, Teams teams) {
        Entry entry = entries.get(id);
        if (entry != null) return entry.visible(actor);
        TeamView team = teams.team(id);
        return team != null && team.member(actor);
    }
    public Page page(UUID actor, String mode, String query, int requestedPage, Accounts accounts) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (q.length() > 64) throw new IllegalArgumentException("query_limit");
        int page = Math.max(0, Math.min(1024, requestedPage));
        List<Person> people = new ArrayList<>();
        List<View> lists = new ArrayList<>();
        Map<UUID, String> book = contacts.getOrDefault(actor, Map.of());
        boolean wantsPeople = mode.equals("contacts") || mode.equals("picker");
        if (wantsPeople) {
            Set<UUID> listed = new java.util.HashSet<>();
            for (var row : book.entrySet()) {
                String address = accounts.address(row.getKey());
                if (address != null && (address + " " + row.getValue()).toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    people.add(new Person(row.getKey(), address, row.getValue()));
                    listed.add(row.getKey());
                }
            }
            if (mode.equals("picker") && !q.isBlank()) {
                for (Person found : accounts.search(q)) if (listed.add(found.id())) people.add(found);
            }
        }
        
        for (Entry entry : related(actor)) {
            boolean permitted = entry.visible(actor);
            if (mode.equals("picker")) permitted = switch (entry.kind) {
                case "personal" -> entry.owner.equals(actor);
                case "mailing" -> entry.manager(actor);
                default -> entry.members.containsKey(actor);
            };
            if (permitted && (mode.equals("picker") || mode.equals(entry.kind)) && entry.name.toLowerCase(java.util.Locale.ROOT).contains(q)) lists.add(entry.summary(actor));
        }
        int start = page * PAGE_SIZE;
        List<Person> personPage = people.stream().skip(start).limit(PAGE_SIZE).toList();
        int listStart = Math.max(0, start - people.size());
        List<View> listPage = lists.stream().skip(listStart).limit(PAGE_SIZE - personPage.size()).toList();
        return new Page(mode, q, page, start + PAGE_SIZE < people.size() + lists.size(), revision, personPage, listPage);
    }
    


 
    private Map<UUID, List<Entry>> byAccount;
    private long indexedRevision = -1;
    private List<Entry> related(UUID account) {
        if (byAccount == null || indexedRevision != revision) {
            Map<UUID, List<Entry>> built = new java.util.HashMap<>();
            for (Entry entry : entries.values()) {
                built.computeIfAbsent(entry.owner, ignored -> new ArrayList<>()).add(entry);
                for (UUID member : entry.members.keySet()) if (!member.equals(entry.owner)) built.computeIfAbsent(member, ignored -> new ArrayList<>()).add(entry);
            }
            byAccount = built;
            indexedRevision = revision;
        }
        return byAccount.getOrDefault(account, List.of());
    }
    private long ownedCount(UUID account) { return related(account).stream().filter(entry -> entry.owner.equals(account)).count(); }
    private static void requireAccount(UUID id, Accounts accounts) {
        if (id == null || accounts.address(id) == null) throw new IllegalArgumentException("account_not_found");
    }
    public String save() {
        JsonObject root = new JsonObject();
        root.addProperty("revision", revision);
        JsonArray books = new JsonArray();
        contacts.forEach((owner, book) -> book.forEach((id, alias) -> {
            JsonObject row = new JsonObject();
            row.addProperty("owner", owner.toString()); row.addProperty("id", id.toString()); row.addProperty("alias", alias); books.add(row);
        }));
        root.add("contacts", books);
        root.add("entries", JSON.toJsonTree(entries.values()));
        return JSON.toJson(root);
    }
    public static AntmailDirectory load(String input) {
        AntmailDirectory directory = new AntmailDirectory();
        if (input == null || input.isBlank()) return directory;
        try {
            JsonObject root = JsonParser.parseString(input).getAsJsonObject();
            try { directory.revision = root.has("revision") ? Math.max(0, root.get("revision").getAsLong()) : 0; } catch (RuntimeException ignored) { }
            for (var element : (root.has("contacts") && root.get("contacts").isJsonArray() ? root.getAsJsonArray("contacts") : new JsonArray())) {
                try {
                    var row = element.getAsJsonObject();
                    UUID owner = UUID.fromString(row.get("owner").getAsString());
                    UUID id = UUID.fromString(row.get("id").getAsString());
                    String alias = !row.has("alias") || row.get("alias").isJsonNull() ? "" : row.get("alias").getAsString();
                    var book = directory.contacts.computeIfAbsent(owner, ignored -> new LinkedHashMap<>());
                    if (alias.length() <= 48 && book.size() < MAX_CONTACTS) book.put(id, alias);
                } catch (RuntimeException ignored) { }
            }
            for (var element : (root.has("entries") && root.get("entries").isJsonArray() ? root.getAsJsonArray("entries") : new JsonArray())) {
                try {
                    Entry row = JSON.fromJson(element, Entry.class);
                    if (row.id == null || row.owner == null || row.managers == null || row.members == null
                            || !List.of("personal", "mailing", "conversation").contains(row.kind)
                            || row.members.size() > AntmailRecipients.MAX_RECIPIENTS || row.members.containsKey(null)
                            || row.members.containsValue(null) || !row.kind.equals("personal") && !row.members.containsKey(row.owner)) continue;
                    row.name = name(row.name);
                    row.managers.retainAll(row.members.keySet());
                    if (row.declined == null) row.declined = new LinkedHashSet<>();
                    row.declined.remove(null);
                    row.declined.removeAll(row.members.keySet());
                    while (row.declined.size() > MAX_DECLINED) row.declined.remove(row.declined.iterator().next());
                    directory.revision = Math.max(directory.revision, row.revision);
                    directory.entries.put(row.id, row);
                } catch (RuntimeException ignored) { }
            }
        } catch (RuntimeException ignored) { }
        return directory;
    }
}
