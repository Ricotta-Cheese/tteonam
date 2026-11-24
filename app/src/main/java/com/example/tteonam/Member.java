package com.example.tteonam;

public class Member {
    private String name;
    private boolean leader;

    public Member(String name, boolean Leader) {
        this.name = name;
        this.leader = Leader;
    }
    public Member() {}

    public String getName() {
        return name;
    }

    public boolean isLeader() {
        return leader;
    }
}
