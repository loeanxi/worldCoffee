package cn.lx.worldcoffee.admin.controller;

import cn.lx.worldcoffee.admin.service.AdminModerationService;
import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.community.domain.from.ReportHandleFrom;
import cn.lx.worldcoffee.community.domain.vo.ReportReviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/community")
@RequiredArgsConstructor
public class AdminCommunityManageController {

    private final AdminModerationService adminModerationService;

    @GetMapping("/reports")
    public Result<List<ReportReviewVO>> listReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(adminModerationService.listReports(status, page, size));
    }

    @PostMapping("/reports/{id}/handle")
    public Result<Void> handleReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportHandleFrom from) {
        adminModerationService.handleReport(id, from);
        return Result.success(null);
    }
}
