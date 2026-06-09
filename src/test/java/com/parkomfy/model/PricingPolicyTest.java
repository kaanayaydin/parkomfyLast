package com.parkomfy.model;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PricingPolicyTest {

    private PricingPolicy policy;

    @Before
    public void setUp() {
        policy = new PricingPolicy("POL-001", 10.0);
        policy.setFirstHourRate(15.0);
        policy.setFreeMinutes(15);
        policy.setMaxDailyRate(80.0);
    }

    @Test
    public void calculateFee_withinFreeMinutesIsZero() {
        assertEquals(0.0, policy.calculateFee(10), 0.001);
        assertEquals(0.0, policy.calculateFee(15), 0.001);
    }

    @Test
    public void calculateFee_firstHourUsesFirstHourRate() {
        assertEquals(15.0, policy.calculateFee(30), 0.001);
        assertEquals(15.0, policy.calculateFee(75), 0.001);
    }

    @Test
    public void calculateFee_additionalHoursRoundUp() {
        // 16 free + 61 billable = 2 hours -> 15 + 10 = 25
        assertEquals(25.0, policy.calculateFee(76), 0.001);
        // 16 free + 121 billable = 3 hours -> 15 + 20 = 35
        assertEquals(35.0, policy.calculateFee(136), 0.001);
    }

    @Test
    public void calculateFee_respectsDailyMaximum() {
        policy.setFreeMinutes(0);
        assertEquals(80.0, policy.calculateFee(24 * 60), 0.001);
    }
}
