package com.popman.arca.service;

import com.popman.arca.entity.BannedEmail;

import java.util.List;

public interface BannedEmailService {
    boolean isBannedV1(String email);
    String banV1(String email, String reason);
    String unbanV1(String email);
    List<BannedEmail> listAllV1();
}
