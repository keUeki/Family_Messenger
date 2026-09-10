package com.shanyangcode.userservice.model.vo;

import lombok.Data;


@Data
public class UploadUrlResponse {
    // URL to upload the file to
    public String uploadUrl;

    // URL to download the file from
    public String downloadUrl;
}