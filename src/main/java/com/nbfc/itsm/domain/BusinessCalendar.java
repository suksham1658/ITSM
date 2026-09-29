package com.nbfc.itsm.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalTime;

@Entity
@Table(name = "business_calendar")
public class BusinessCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_calendar_id")
    private Long businessCalendarId;

    // TINYINT in V1; the column definition lets ddl-auto=validate accept it (INTEGER alone would not).
    @Column(name = "weekday_iso", nullable = false, columnDefinition = "tinyint")
    private int weekdayIso;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "timezone_id", nullable = false, length = 64)
    private String timezoneId = "India Standard Time";

    @Column(name = "is_working_day", nullable = false)
    private boolean workingDay = true;

    public Long getBusinessCalendarId() {
        return businessCalendarId;
    }

    public void setBusinessCalendarId(Long businessCalendarId) {
        this.businessCalendarId = businessCalendarId;
    }

    public int getWeekdayIso() {
        return weekdayIso;
    }

    public void setWeekdayIso(int weekdayIso) {
        this.weekdayIso = weekdayIso;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public void setTimezoneId(String timezoneId) {
        this.timezoneId = timezoneId;
    }

    public boolean isWorkingDay() {
        return workingDay;
    }

    public void setWorkingDay(boolean workingDay) {
        this.workingDay = workingDay;
    }
}
