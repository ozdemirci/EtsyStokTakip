package dev.oasis.stockify.mapper;

import dev.oasis.stockify.dto.ContactMessageDTO;
import dev.oasis.stockify.model.ContactMessage;
import org.springframework.stereotype.Component;

/**
 * Mapper for ContactMessage entities and DTOs
 */
@Component
public class ContactMessageMapper {
    
    /**
     * Convert DTO to entity
     */
    public ContactMessage toEntity(ContactMessageDTO dto) {
        if (dto == null) {
            return null;
        }
        
        ContactMessage entity = new ContactMessage();
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setEmail(dto.getEmail());
        entity.setPhone(dto.getPhone());
        entity.setCompany(dto.getCompany());
        entity.setSubject(dto.getSubject());
        entity.setMessage(dto.getMessage());
        entity.setIpAddress(dto.getIpAddress());
        entity.setUserAgent(dto.getUserAgent());
        
        return entity;
    }
    
    /**
     * Convert entity to DTO
     */
    public ContactMessageDTO toDTO(ContactMessage entity) {
        if (entity == null) {
            return null;
        }
        
        return ContactMessageDTO.builder()
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .company(entity.getCompany())
                .subject(entity.getSubject())
                .message(entity.getMessage())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .build();
    }
}
