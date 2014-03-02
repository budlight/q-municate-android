package com.quickblox.qmunicate.model;

import com.quickblox.module.users.model.QBUser;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Friend implements Serializable {

    private Integer id;
    private String fullname;
    private String email;
    private String phone;
    private Integer fileId;
    private String status;
    private boolean online;

    public Friend(QBUser user) {
        this.id = user.getId();
        this.fullname = user.getFullName();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.fileId = user.getFileId();
        this.online = false;
    }

    public static List<Friend> toFriends(List<QBUser> users) {
        List<Friend> friends = new ArrayList<Friend>();
        for (QBUser user : users) {
            friends.add(new Friend(user));
        }
        return friends;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullname() {
        return fullname;
    }

    public void setFullname(String fullname) {
        this.fullname = fullname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getFileId() {
        return fileId;
    }

    public void setFileId(int fileId) {
        this.fileId = fileId;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
