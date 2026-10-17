package com.spruce.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientRegistry {
    private static final Map<String, Client> DYNAMIC = new LinkedHashMap<>();

    private ClientRegistry() {
    }

    public static void registerDynamic(List<Client> clients) {
        DYNAMIC.clear();
        for (Client client : clients) {
            DYNAMIC.put(client.id, client);
        }
    }

    public static List<Client> builtIn() {
        return List.of();
    }

    public static List<Client> all() {
        List<Client> clients = new ArrayList<>(builtIn());
        clients.addAll(DYNAMIC.values());
        return clients;
    }

    public static Client byId(String id) {
        Client dynamic = DYNAMIC.get(id);
        if (dynamic != null) {
            return dynamic;
        }
        for (Client client : builtIn()) {
            if (client.id.equals(id)) {
                return client;
            }
        }
        return null;
    }
}
