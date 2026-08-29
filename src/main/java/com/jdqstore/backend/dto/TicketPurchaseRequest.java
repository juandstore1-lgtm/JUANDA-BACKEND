package com.jdqstore.backend.dto;
import lombok.Data;
import java.util.List;

@Data
public class TicketPurchaseRequest {
    private List<Long> ticketIds;
    private BuyerDTO buyer;
}
