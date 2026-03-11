package com.communitysport.coach.dto;

import java.util.List;

public class CoachApplicationSubmitRequest {

    private String specialty;

    private String intro;

    private List<String> certFiles;

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public List<String> getCertFiles() {
        return certFiles;
    }

    public void setCertFiles(List<String> certFiles) {
        this.certFiles = certFiles;
    }
}
