package org.hylly.mtk2garmin;

import java.util.ArrayList;

class Way extends OSMElementBase {
    String role = "all";
    ArrayList<Long> refs = new ArrayList<>();

    String getRole() {
        return role;
    }

    void setRole(String role) {
        this.role = role;
    }
}
