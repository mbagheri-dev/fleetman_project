package com.virtualpairprogrammers.tracker.data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalPosition;
import org.springframework.stereotype.Repository;

import com.virtualpairprogrammers.tracker.domain.VehicleBuilder;
import com.virtualpairprogrammers.tracker.domain.VehicleNotFoundException;
import com.virtualpairprogrammers.tracker.domain.VehiclePosition;

/**
 * This is a quick and dirty dev-standin (for local testing) that stores vehicle position reports
 * in a memory structure. 
 * 
 * Thread safety is NOT OFFERED; remember this is intended to be used in local development.
 * 
 * Use the database backed implementation in a production, clustered or cloud environment.
 */
@Repository
public class DataBasicInMemoryImpl implements Data 
{
	private static final BigDecimal MPS_TO_MPH_FACTOR = new BigDecimal("2.236936");
	private GeodeticCalculator geoCalc = new GeodeticCalculator();
	private Map<String,TreeSet<VehiclePosition>> positionDatabase;

	public DataBasicInMemoryImpl()
	{
		positionDatabase = new HashMap<>();
	}

	@Override
	public void updatePosition(VehiclePosition data)
	{
		String vehicleName = data.getName();
		TreeSet<VehiclePosition> positions = positionDatabase.get(vehicleName);
		if (positions == null) 
		{
			positions = new TreeSet<>();
			positionDatabase.put(vehicleName, positions);
		}
		BigDecimal speed = calculateSpeedInMph(vehicleName, data);
		VehiclePosition vehicleWithSpeed = new VehicleBuilder().withVehiclePostion(data).withSpeed(speed).build();
		positions.add(vehicleWithSpeed);
	}

	@Override
	public VehiclePosition getLatestPositionFor(String vehicleName) throws VehicleNotFoundException
	{
		TreeSet<VehiclePosition> reportsForThisVehicle = positionDatabase.get(vehicleName);
		if (reportsForThisVehicle == null) throw new VehicleNotFoundException();
		return reportsForThisVehicle.first();
	}

	private BigDecimal calculateSpeedInMph(String vehicleName, VehiclePosition newPosition)
	{	
		TreeSet<VehiclePosition> positions = positionDatabase.get(vehicleName);
		if (positions.isEmpty()) return null;

		VehiclePosition posB = newPosition;
		VehiclePosition posA = positions.first(); // confusing - this is actually the last report recorded

		long timeAinMillis = posA.getTimestamp().getTime();
		long timeBinMillis = posB.getTimestamp().getTime();
		long timeInMillis = timeBinMillis - timeAinMillis;
		if (timeInMillis == 0) return new BigDecimal("0");

		BigDecimal timeInSeconds = new BigDecimal(timeInMillis / 1000.0);

		GlobalPosition pointA = new GlobalPosition(posA.getLat().doubleValue(), posA.getLongitude().doubleValue(), 0.0);
		GlobalPosition pointB = new GlobalPosition(posB.getLat().doubleValue(), posB.getLongitude().doubleValue(), 0.0);

		double distance = geoCalc.calculateGeodeticCurve(Ellipsoid.WGS84, pointA, pointB).getEllipsoidalDistance(); // Distance between Point A and Point B
		BigDecimal distanceInMetres = new BigDecimal (""+ distance);

		BigDecimal speedInMps = distanceInMetres.divide(timeInSeconds, RoundingMode.HALF_UP);
		BigDecimal milesPerHour = speedInMps.multiply(MPS_TO_MPH_FACTOR);
		return milesPerHour;
	}

	@Override
	public void addAllReports(VehiclePosition[] allReports) {
		for (VehiclePosition next: allReports)
		{
			this.updatePosition(next);
		}
	}

	@Override
	public Set<VehiclePosition> getLatestPositionsOfAllVehicles() {
		Set<VehiclePosition> results = new HashSet<>();

		for (TreeSet<VehiclePosition> reports: this.positionDatabase.values())
		{
			if (!reports.isEmpty()) results.add(reports.first());				
		}
		return results;
	}

	@Override
	public Collection<VehiclePosition> getHistoryFor(String vehicleName) throws VehicleNotFoundException 
	{
		return this.positionDatabase.get(vehicleName);
	}
}
