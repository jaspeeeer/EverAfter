package com.wedding.planner.config;

import com.wedding.planner.domain.ChecklistTemplate;
import com.wedding.planner.domain.ChecklistTemplateItem;
import com.wedding.planner.domain.Role;
import com.wedding.planner.domain.RoleName;
import com.wedding.planner.domain.User;
import com.wedding.planner.domain.VendorCategory;
import com.wedding.planner.domain.VendorTemplate;
import com.wedding.planner.domain.VendorTemplateItem;
import com.wedding.planner.repository.ChecklistTemplateRepository;
import com.wedding.planner.repository.RoleRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.repository.VendorTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the three RBAC roles and a bootstrap admin account on startup (idempotent). This gives a
 * known way in for administrative flows without exposing admin creation over the public API.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ChecklistTemplateRepository checklistTemplateRepository;
    private final VendorTemplateRepository vendorTemplateRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           ChecklistTemplateRepository checklistTemplateRepository,
                           VendorTemplateRepository vendorTemplateRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email}") String adminEmail,
                           @Value("${app.admin.password}") String adminPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.checklistTemplateRepository = checklistTemplateRepository;
        this.vendorTemplateRepository = vendorTemplateRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (RoleName roleName : RoleName.values()) {
            roleRepository.findByName(roleName)
                    .orElseGet(() -> roleRepository.save(new Role(roleName)));
        }

        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
            User admin = new User(adminEmail, passwordEncoder.encode(adminPassword), "System", "Admin");
            admin.addRole(adminRole);
            userRepository.save(admin);
            log.info("Seeded bootstrap admin account: {}", adminEmail);
        }

        seedStarterTemplates();
    }

    /**
     * Seeds one starter checklist + vendor template so planners have something to apply before
     * an admin authors any. Runs only while the catalog is completely empty, so admin edits and
     * deletions stick.
     */
    private void seedStarterTemplates() {
        if (checklistTemplateRepository.count() == 0) {
            ChecklistTemplate checklist = new ChecklistTemplate(
                    "Classic Wedding Checklist",
                    "The essential to-dos for a traditional wedding, counted back from the big day.");
            record Preset(String title, String note, Integer days) {
            }
            for (Preset p : new Preset[] {
                    new Preset("Set the budget", "Agree the overall number and who contributes.", 365),
                    new Preset("Draft the guest list", null, 330),
                    new Preset("Book venue", "Ceremony and reception.", 300),
                    new Preset("Book photographer", null, 240),
                    new Preset("Book videographer", null, 240),
                    new Preset("Book caterer & plan menu", null, 210),
                    new Preset("Order the dress / attire", "Allow time for fittings.", 180),
                    new Preset("Send save-the-dates", null, 150),
                    new Preset("Book florist", null, 120),
                    new Preset("Send invitations", null, 90),
                    new Preset("Final dress fitting", null, 30),
                    new Preset("Confirm final headcount with caterer", null, 14),
            }) {
                checklist.addItem(new ChecklistTemplateItem(p.title(), p.note(), p.days()));
            }
            checklistTemplateRepository.save(checklist);
            log.info("Seeded starter checklist template: {}", checklist.getName());
        }

        if (vendorTemplateRepository.count() == 0) {
            VendorTemplate vendors = new VendorTemplate(
                    "Essential Vendors",
                    "The supplier slots most weddings need — add contacts as you shortlist.");
            vendors.addItem(new VendorTemplateItem("Venue", VendorCategory.VENUE));
            vendors.addItem(new VendorTemplateItem("Caterer", VendorCategory.CATERING));
            vendors.addItem(new VendorTemplateItem("Photographer", VendorCategory.PHOTOGRAPHY));
            vendors.addItem(new VendorTemplateItem("Videographer", VendorCategory.VIDEOGRAPHY));
            vendors.addItem(new VendorTemplateItem("Florist", VendorCategory.FLORIST));
            vendors.addItem(new VendorTemplateItem("Band / DJ", VendorCategory.MUSIC));
            vendorTemplateRepository.save(vendors);
            log.info("Seeded starter vendor template: {}", vendors.getName());
        }
    }
}
