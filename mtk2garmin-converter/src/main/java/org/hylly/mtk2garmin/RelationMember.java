package org.hylly.mtk2garmin;

class RelationMember {
    long id;
    String type;
    String role;

    long getId() {
        return id;
    }

    void setId(long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    void setType() {
        this.type = "way";
    }

    String getRole() {
        return role;
    }

    void setRole(String role) {
        this.role = role;
    }
}
