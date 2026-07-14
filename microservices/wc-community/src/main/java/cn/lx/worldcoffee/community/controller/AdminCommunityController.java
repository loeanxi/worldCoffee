package cn.lx.worldcoffee.community.controller;

import cn.lx.worldcoffee.common.result.Result;
import cn.lx.worldcoffee.community.domain.from.ReportHandleFrom;
import cn.lx.worldcoffee.community.domain.vo.ReportReviewVO;
import cn.lx.worldcoffee.community.service.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/community")
@RequiredArgsConstructor
public class AdminCommunityController {

    private final CommunityService communityService;

    @GetMapping("/reports")
    public Result<List<ReportReviewVO>> listReports(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(communityService.listReports(status, page, size));
    }

    @PostMapping("/reports/{id}/handle")
    public Result<Void> handleReport(
            @PathVariable Long id,
            @Valid @RequestBody ReportHandleFrom from) {
        communityService.handleReport(id, from);
        return Result.success(null);
    }
}
