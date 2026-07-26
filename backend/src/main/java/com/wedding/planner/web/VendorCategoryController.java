package com.wedding.planner.web;

import com.wedding.planner.dto.VendorCategoryDtos.VendorCategoryResponse;
import com.wedding.planner.service.VendorCategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Active vendor categories for the pickers. Readable by any authenticated user (planners and
 * couples build vendors); admin management lives at {@code /api/admin/vendor-categories}.
 */
@RestController
@RequestMapping("/api/vendor-categories")
public class VendorCategoryController {

    private final VendorCategoryService vendorCategoryService;

    public VendorCategoryController(VendorCategoryService vendorCategoryService) {
        this.vendorCategoryService = vendorCategoryService;
    }

    @GetMapping
    public List<VendorCategoryResponse> list() {
        return vendorCategoryService.listActive();
    }
}
