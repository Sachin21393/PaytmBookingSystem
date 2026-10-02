package com.paytm.project.service;

import com.paytm.project.dto.CreateShowRequest;
import com.paytm.project.dto.ShowResponse;

public interface ShowService {

    ShowResponse createShow(CreateShowRequest request);

    ShowResponse getShow(Long showId);
}
