package de.nonnull.hcu.adaxplugin.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import de.nonnull.hcu.adaxplugin.config.RoomConfig;

public class ConversionServiceTest {

    private static final double DELTA = 1e-9;

    private ConversionService conversionService;

    @Before
    public void setUp() {
        conversionService = new ConversionService();
    }

    @Test
    public void convertAdaxToHcuTemperature_null_returnsNull() {
        assertNull(conversionService.convertAdaxToHcuTemperature(null));
    }

    @Test
    public void convertAdaxToHcuTemperature_dividesByHundred() {
        assertEquals(20.0, conversionService.convertAdaxToHcuTemperature(2000), DELTA);
        assertEquals(5.0, conversionService.convertAdaxToHcuTemperature(500), DELTA);
    }

    @Test
    public void convertAdaxToHcuTemperature_clampsToBounds() {
        assertEquals(60.0, conversionService.convertAdaxToHcuTemperature(10_000), DELTA);
        assertEquals(-50.0, conversionService.convertAdaxToHcuTemperature(-10_000), DELTA);
    }

    @Test
    public void convertHcuSetPointToAdax_null_returnsNull() {
        assertNull(conversionService.convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig(0.0), null));
    }

    @Test
    public void convertHcuSetPointToAdax_multipliesByHundred() {
        assertEquals(Integer.valueOf(2000),
                conversionService.convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig(0.0), 20.0));
    }

    @Test
    public void convertHcuSetPointToAdax_appliesOffset() {
        assertEquals(Integer.valueOf(2150),
                conversionService.convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig(1.5), 20.0));
    }

    @Test
    public void convertHcuSetPointToAdax_clampsToAdaxBounds() {
        // below the minimum target temperature (500)
        assertEquals(Integer.valueOf(500),
                conversionService.convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig(0.0), 2.0));
        // above the maximum target temperature (3500)
        assertEquals(Integer.valueOf(3500),
                conversionService.convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig(0.0), 40.0));
    }

    private static RoomConfig roomConfig(double setPointTemperatureOffset) {
        final var config = new RoomConfig();
        config.setSetPointTemperatureOffset(setPointTemperatureOffset);
        return config;
    }
}
