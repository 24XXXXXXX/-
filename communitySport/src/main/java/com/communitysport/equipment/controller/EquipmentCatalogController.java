package com.communitysport.equipment.controller;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.communitysport.equipment.dto.EquipmentCategoryItem;
import com.communitysport.equipment.dto.EquipmentDetailResponse;
import com.communitysport.equipment.dto.EquipmentPageResponse;
import com.communitysport.equipment.service.EquipmentCatalogService;
import com.communitysport.upload.dto.FileUploadResponse;

@RestController
public class EquipmentCatalogController {

    private final EquipmentCatalogService equipmentCatalogService;

    public EquipmentCatalogController(EquipmentCatalogService equipmentCatalogService) {
        this.equipmentCatalogService = equipmentCatalogService;
    }

    @GetMapping("/api/equipment/categories")
    public List<EquipmentCategoryItem> categories() {
        // 用户端：查询商品分类列表。
        //
        // 该接口是“公共读接口”：
        // - 不需要登录（不涉及用户隐私/资产）
        // - 仅用于前端渲染筛选条件
        return equipmentCatalogService.listCategories();
    }

    @GetMapping("/api/equipments")
    public EquipmentPageResponse list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "keyword", required = false) String keyword
    ) {
        // 用户端：商品目录分页。
        // - page/size：分页参数（Service 会做默认值与范围裁剪）
        // - categoryId：可选分类过滤
        // - keyword：可选模糊搜索
        //
        // 过滤规则（在 Service 内完成）：
        // - 强制只返回 ON_SALE（上架）商品，避免用户端看到下架/禁售数据
        return equipmentCatalogService.listEquipments(page, size, categoryId, keyword);
    }

    @GetMapping("/api/equipments/{id}")
    public EquipmentDetailResponse detail(@PathVariable("id") Long id) {
        // 用户端：商品详情。
        // - 该接口同样是公共读接口
        // - Service 会强制校验 status=ON_SALE，否则按 NOT_FOUND 处理
        return equipmentCatalogService.getEquipment(id);
    }

    @PostMapping("/api/equipments/{id}/cover")
    public FileUploadResponse uploadCover(Authentication authentication, @PathVariable("id") Long id, @RequestParam("file") MultipartFile file) {
        // 管理/员工侧：上传并更新商品封面。
        //
        // 为什么权限校验放在 Controller（requireStaff）？
        // - 这属于“写接口”，需要在进入业务逻辑前快速拒绝未授权请求
        // - 控制器离 HTTP 边界最近，能更清晰表达“哪个 URL 需要什么角色”
        // - Service 仍会做参数合法性与数据存在性校验（防止绕过 Controller 的内部调用）
        requireStaff(authentication);
        String url = equipmentCatalogService.uploadAndUpdateCover(id, file);
        FileUploadResponse resp = new FileUploadResponse();
        resp.setFileName(file == null ? null : file.getOriginalFilename());
        resp.setUrl(url);
        return resp;
    }

    private void requireStaff(Authentication authentication) {
        // 角色边界：只有 ADMIN 或 STAFF 才能进行后台商品维护操作。
        // 注意：这里没有复用全局注解（如 @PreAuthorize），而是选择显式遍历 authorities。
        // 这样做的优点是：
        // - 规则直观、与其他模块（course/venue）保持一致
        // - 便于教学与排查（断点/日志更直接）
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        boolean ok = false;
        if (authorities != null) {
            for (GrantedAuthority a : authorities) {
                if (a == null) {
                    continue;
                }
                String auth = a.getAuthority();
                if ("ROLE_ADMIN".equals(auth) || "ROLE_STAFF".equals(auth)) {
                    ok = true;
                    break;
                }
            }
        }
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
