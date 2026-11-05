package com.itdesk;

/** The signed-in person, as loaded from the session. */
record User(String id, String name, String email, String role) {}
