package com.beauver.printbridge.support;

import com.beauver.printbridge.slicing.SlicePlan;
import com.beauver.printbridge.slicing.client.SliceResponse;
import com.beauver.printbridge.slicing.client.SlicerClient;
import com.beauver.printbridge.slicing.client.SlicerException;
import com.beauver.printbridge.slicing.client.SlicerPrinter;
import com.beauver.printbridge.slicing.client.SlicerProfiles;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.enterprise.inject.Alternative;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Replaces the slicer service in @QuarkusTest tests: records the plans and answers as told.
 * A singleton (no client proxy), so tests can set its public fields directly.
 */
@Alternative
@Priority(1)
@Singleton
public class FakeSlicerClient extends SlicerClient {

    public final List<SlicePlan> plans = new CopyOnWriteArrayList<>();
    public boolean configured;
    public RuntimeException failure;
    public Function<SlicePlan, SliceResponse> answer;

    public FakeSlicerClient() {
        reset();
    }

    public final void reset() {
        plans.clear();
        configured = true;
        failure = null;
        answer = plan -> new SliceResponse(plan.plates().stream()
                .map(index -> new SliceResponse.Plate(index, 20.0 + index, 600L * index,
                        List.of(new SliceResponse.Filament("PLA", "#00FF00", 20.0 + index)), null))
                .toList());
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String version() {
        throwIfFailing();
        return "2.5.0";
    }

    @Override
    public List<SlicerPrinter> printers() {
        throwIfFailing();
        return List.of(new SlicerPrinter("Bambu Lab P1S", "BBL", List.of(new SlicerPrinter.Nozzle(
                new BigDecimal("0.4"), "Bambu Lab P1S 0.4 nozzle", new SlicerPrinter.Bed(256, 256, 250)))));
    }

    @Override
    public SlicerProfiles profiles(String printer, BigDecimal nozzle) {
        throwIfFailing();
        return new SlicerProfiles(printer + " " + nozzle.toPlainString() + " nozzle", List.of("0.20mm Standard"),
                List.of("PLA Basic"));
    }

    @Override
    public SliceResponse slice(Path file, SlicePlan plan, Duration timeout) {
        plans.add(plan);
        throwIfFailing();
        return answer.apply(plan);
    }

    private void throwIfFailing() {
        if (failure != null) {
            throw failure;
        }
    }

    public static SlicerException unreachable() {
        return new SlicerException("The slicer can't be reached");
    }
}
