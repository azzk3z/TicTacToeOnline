package com.tictactoe.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_rooms")
public class GameRoom {
    public static final int CODE_LENGTH = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = CODE_LENGTH)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false, updatable = false, unique = true)
    private User host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", unique = true)
    private User guest;

    protected GameRoom() {
    }

    public GameRoom(String code, User host) {
        this.code = code;
        this.host = host;
    }

    public String getCode() {
        return code;
    }

    public User getHost() {
        return host;
    }

    public User getGuest() {
        return guest;
    }

    public boolean isHost(Long userId) {
        return host.getId().equals(userId);
    }

    public boolean hasMember(Long userId) {
        return isHost(userId) || (guest != null && guest.getId().equals(userId));
    }

    public void setGuest(User guest) {
        this.guest = guest;
    }
}
