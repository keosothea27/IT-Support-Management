package com.itdesk;

import java.time.Year;
import java.util.*;

/**
 * The tables the API exposes. Only names listed here are ever put into SQL,
 * so request data can never choose a table or column name.
 * Column types: T text (default), D date, N decimal number, I whole number.
 */
final class Entities {
    private Entities() {}

    static final class Col {
        final String key;
        final char type;
        Col(String key, char type) { this.key = key; this.type = type; }
    }

    static final class Ent {
        final String key, table, prefix;
        final int pad;
        final List<Col> cols = new ArrayList<>();
        final List<String> required;

        Ent(String key, String table, String prefix, int pad, String spec, String required) {
            this.key = key; this.table = table; this.prefix = prefix; this.pad = pad;
            for (String part : spec.split(",")) {
                String[] kv = part.split(":");
                cols.add(new Col(kv[0], kv.length > 1 ? kv[1].charAt(0) : 'T'));
            }
            this.required = List.of(required.split(","));
        }

        Col col(String key) {
            for (Col c : cols) if (c.key.equals(key)) return c;
            return null;
        }

        /** Ticket numbers carry the year, e.g. TKT-2026-0007. */
        String newPrefix() { return key.equals("tickets") ? "TKT-" + Year.now().getValue() + "-" : prefix; }
    }

    static final Map<String, Ent> ALL = new LinkedHashMap<>();

    private static void add(String key, String table, String prefix, int pad, String spec, String required) {
        ALL.put(key, new Ent(key, table, prefix, pad, spec, required));
    }

    static {
        add("assets", "assets", "IT-", 4, "name,category,brand,serial,dept,owner,location,purchase:D,price:N,warranty:D,status,ip,mac,notes", "name");
        add("tickets", "tickets", "TKT-", 4, "requester,category,description,priority,tech,status,created:D,hours:N,resolution", "requester,description");
        add("comments", "ticket_comments", "CMT-", 3, "ticket,author,comment,date:D", "ticket,comment");
        add("licenses", "licenses", "LIC-", 3, "software,version,type,seats:I,purchase:D,expires:D,notes", "software");
        add("swrecords", "software_records", "SWR-", 3, "software,type,target,date:D,cost:N,notes", "software");
        add("employees", "employees", "EMP-", 3, "name,dept,location,email,status", "name");
        add("departments", "departments", "DEP-", 2, "name", "name");
        add("users", "app_users", "USR-", 2, "name,email,role,status", "name,email");
        add("movements", "asset_movements", "MOV-", 3, "asset,type,from_party,to_party,date:D,notes", "asset");
        add("maintenance", "maintenance_jobs", "MNT-", 3, "asset,type,vendor,date:D,status,tech,cost:N,parts,notes", "asset");
        add("vendors", "vendors", "VEN-", 2, "name,contact,phone,email,service", "name");
        add("ips", "ip_addresses", "IP-", 3, "ip,device,mac,vlan,location,status", "ip");
        add("docs", "network_docs", "DOC-", 2, "title,type,url,notes", "title");
        add("locations", "locations", "LOC-", 2, "name,address", "name");
        add("categories", "categories", "CAT-", 2, "name,type,sla:I", "name");
    }
}
