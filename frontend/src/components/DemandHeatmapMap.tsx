"use client";

import { CircleMarker, MapContainer, Popup, TileLayer, Tooltip } from "react-leaflet";

export type DemandMapZone = {
  location: string;
  wishlistSaves: number;
  openRequests: number;
  needRequests: number;
  demandScore: number;
};

const campusCenter: [number, number] = [12.9337137, 77.6923234];

const approximateZoneCoordinates: Record<string, [number, number]> = {
  "main library plaza": [12.93395, 77.69215],
  "student union atrium": [12.9335, 77.69265],
  "north gate commons": [12.93445, 77.69245],
  "hall 4 (boys quad)": [12.93395, 77.69275],
  "24/7 library hub": [12.93395, 77.69215],
  "main cafeteria": [12.9335, 77.69265],
  "north campus": campusCenter
};

const getCoordinates = (location: string): [number, number] =>
  approximateZoneCoordinates[location.trim().toLowerCase()] ?? campusCenter;

const getDemandColor = (intensity: number) => {
  const start = { red: 22, green: 163, blue: 74 };
  const end = { red: 234, green: 88, blue: 12 };
  const red = Math.round(start.red + (end.red - start.red) * intensity);
  const green = Math.round(start.green + (end.green - start.green) * intensity);
  const blue = Math.round(start.blue + (end.blue - start.blue) * intensity);
  return `rgb(${red}, ${green}, ${blue})`;
};

export default function DemandHeatmapMap({ zones }: { zones: DemandMapZone[] }) {
  const highestScore = Math.max(...zones.map((zone) => zone.demandScore), 1);

  return (
    <MapContainer
      center={campusCenter}
      zoom={17}
      scrollWheelZoom={false}
      className="demand-leaflet-map"
      aria-label="Map of approximate demand zones at New Horizon College of Engineering"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {zones.map((zone) => {
        const intensity = Math.min(zone.demandScore / highestScore, 1);
        const color = getDemandColor(intensity);
        return (
          <CircleMarker
            key={zone.location}
            center={getCoordinates(zone.location)}
            radius={12 + Math.sqrt(zone.demandScore) * 5}
            pathOptions={{
              color,
              fillColor: color,
              fillOpacity: 0.38 + intensity * 0.3,
              weight: 2
            }}
          >
            <Tooltip direction="top" offset={[0, -8]}>
              {zone.location} · {zone.demandScore} interest signals
            </Tooltip>
            <Popup>
              <strong>{zone.location}</strong>
              <br />
              {zone.demandScore} total interest signals
              <br />
              {zone.wishlistSaves} wishlist saves · {zone.openRequests} open requests
              <br />
              {zone.needRequests} public need requests
              <br />
              Marker position is approximate.
            </Popup>
          </CircleMarker>
        );
      })}
    </MapContainer>
  );
}
