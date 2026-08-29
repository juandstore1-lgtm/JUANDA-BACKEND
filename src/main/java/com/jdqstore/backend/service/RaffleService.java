package com.jdqstore.backend.service;

import com.jdqstore.backend.dto.RaffleDTO;
import com.jdqstore.backend.entity.Raffle;
import com.jdqstore.backend.entity.Ticket;
import com.jdqstore.backend.repository.RaffleRepository;
import com.jdqstore.backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.ArrayList;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

@Service
@RequiredArgsConstructor
public class RaffleService {
    private final RaffleRepository repository;
    private final TicketRepository ticketRepository;
    private final JdbcTemplate jdbcTemplate;

    public List<RaffleDTO> findAll() {
        return repository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public RaffleDTO findById(Long id) {
        return repository.findById(id).map(this::mapToDTO).orElseThrow();
    }

    @Transactional
    public RaffleDTO create(RaffleDTO dto) {
        Raffle entity = mapToEntity(dto, new Raffle());
        entity = repository.save(entity);

        // Generate tickets
        final Raffle savedRaffle = entity;
        int total = entity.getTotalTickets();
        int maxTicket = total - 1;
        int digits = String.valueOf(maxTicket).length();
        if (digits < 2) digits = 2; // e.g. 00 to 99
        String formatString = "%0" + digits + "d";

        List<Ticket> tickets = IntStream.rangeClosed(0, maxTicket)
                .mapToObj(i -> Ticket.builder()
                        .raffle(savedRaffle)
                        .ticketNumber(String.format(formatString, i))
                        .status("AVAILABLE")
                        .build())
                .collect(Collectors.toList());

        String sql = "INSERT INTO tickets (raffle_id, ticket_number, status) VALUES (?, ?, ?)";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Ticket ticket = tickets.get(i);
                ps.setLong(1, ticket.getRaffle().getId());
                ps.setString(2, ticket.getTicketNumber());
                ps.setString(3, ticket.getStatus());
            }

            @Override
            public int getBatchSize() {
                return tickets.size();
            }
        });

        return mapToDTO(savedRaffle);
    }

    @Transactional
    public RaffleDTO update(Long id, RaffleDTO dto) {
        Raffle entity = repository.findById(id).orElseThrow();
        entity = mapToEntity(dto, entity);
        return mapToDTO(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        ticketRepository.deleteByRaffleId(id);
        repository.deleteById(id);
    }

    private RaffleDTO mapToDTO(Raffle entity) {
        RaffleDTO dto = new RaffleDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setImageUrl(entity.getImageUrl());
        dto.setPrize(entity.getPrize());
        dto.setTicketPrice(entity.getTicketPrice());
        dto.setTotalTickets(entity.getTotalTickets());
        dto.setAvailableTickets(entity.getAvailableTickets());
        dto.setStartDate(entity.getStartDate());
        dto.setEndDate(entity.getEndDate());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private Raffle mapToEntity(RaffleDTO dto, Raffle entity) {
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setImageUrl(dto.getImageUrl());
        entity.setPrize(dto.getPrize());
        entity.setTicketPrice(dto.getTicketPrice());
        if (entity.getId() == null) {
            entity.setTotalTickets(dto.getTotalTickets());
            entity.setAvailableTickets(dto.getTotalTickets());
        }
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        return entity;
    }
}
