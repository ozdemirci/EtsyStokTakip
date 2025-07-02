package dev.oasis.stockify.controller;

import dev.oasis.stockify.dto.ContactMessageDTO;
import dev.oasis.stockify.model.ContactMessage;
import dev.oasis.stockify.service.ContactMessageService;
import dev.oasis.stockify.util.ControllerTenantUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ContactMessageService contactMessageService;
    private final ControllerTenantUtil tenantResolutionUtil;
    
    @ModelAttribute
    public void setupTenantContext(HttpServletRequest request) {
        tenantResolutionUtil.setupTenantContext(request);
    }

    @GetMapping("/")
    public String home(HttpServletRequest request, Authentication authentication, Model model) {
        // Tenant ID çözümleme - şu anda kullanılmıyor ancak gelecekte gerekebilir
        String tenantId = tenantResolutionUtil.resolveTenantId(request, authentication, false);
        model.addAttribute("currentTenantId", tenantId);
        return "index";
    }
    
    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("contactMessage", new ContactMessageDTO());
        return "contact";
    }
    
    @PostMapping("/contact")
    public String submitContactForm(@Valid @ModelAttribute("contactMessage") ContactMessageDTO contactMessageDTO,
                                  BindingResult bindingResult,
                                  HttpServletRequest request,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        
        if (bindingResult.hasErrors()) {
            log.warn("Contact form validation errors: {}", bindingResult.getAllErrors());
            model.addAttribute("contactMessage", contactMessageDTO);
            model.addAttribute("errorMessage", "Lütfen formu eksiksiz ve doğru şekilde doldurunuz.");
            return "contact";
        }
        
        try {
            ContactMessage savedMessage = contactMessageService.saveContactMessage(contactMessageDTO, request);
            
            log.info("✅ Contact form submitted successfully by {} {} (ID: {})", 
                    contactMessageDTO.getFirstName(), 
                    contactMessageDTO.getLastName(),
                    savedMessage.getId());
            
            redirectAttributes.addFlashAttribute("successMessage", 
                "Mesajınız başarıyla gönderildi! En kısa sürede sizinle iletişime geçeceğiz.");
            
            return "redirect:/contact";
            
        } catch (Exception e) {
            log.error("❌ Error submitting contact form: {}", e.getMessage(), e);
            model.addAttribute("contactMessage", contactMessageDTO);
            model.addAttribute("errorMessage", 
                "Mesajınız gönderilirken bir hata oluştu. Lütfen tekrar deneyiniz veya doğrudan e-posta gönderin.");
            return "contact";
        }
    }
}
