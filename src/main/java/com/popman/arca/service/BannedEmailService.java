package com.popman.arca.service;

import com.popman.arca.entity.BannedEmail;

import java.util.List;

public interface BannedEmailService {
    boolean isBanned(String email);
    String ban(String email, String reason);
    String unban(String email);
    List<BannedEmail> listAll();
}
