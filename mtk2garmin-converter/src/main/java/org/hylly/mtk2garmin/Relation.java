package org.hylly.mtk2garmin;

import java.util.ArrayList;

class Relation extends OSMElementBase {
    final ArrayList<RelationMember> members = new ArrayList<>();

    ArrayList<RelationMember> getMembers() {
        return members;
    }
}
