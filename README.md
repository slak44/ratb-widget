# RATB Widget

A widget that shows [RATB](http://www.stbsa.ro) bus times on the home screen.

Sample usage:
![Sample](./sample.png)

Minimum required version is Nougat 7.1 (API level 25).

Dependencies are managed by Gradle.

### API calls used

Host: https://info.stbsa.ro

- `GET /rp/api/lines`: returns `LinesResponse`
- `GET /rp/api/lines/:id/direction/:dir`: returns `StopLine`
- `GET /rp/api/lines/:id/stops/:stopId`: returns `TimetableResponse`

```typescript
interface Stop {
  id: number;
  lat: number;
  lng: number;
  name: string;
  description: string;
}

interface Org {
  id: number;
  logo: string; // url
}

interface CommonLine {
  id: number;
  name: string;
  type: "TRAM" | "BUS" | "SUBWAY";
  color: string; // hex color
  has_notifications: boolean;
  organization: Org;
  ticket_sms: string | null;
  price_ticket_sms: string | null;
}

interface StopsLine extends CommonLine {
  direction_name_retur: string;
  direction_name_tur: string;
  segment_path: string;
  stops: Stop[];
}

interface HourTimes {
  hour: string;
  minutes: string[];
}

interface TimetableLine extends CommonLine {
  description: string;
  direction: number;
  direction_name: string;
  arriving_time: number;
  arriving_times: {arrivingTime: number, timetable: boolean}[];
  is_timetable: boolean;
  timetable?: HourTimes[];
}

interface TimetableResponse {
  name: string;
  transport_type: string;
  lines: TimetableLine[];
}

interface LinesResponse {
  lines: CommonLine[];
  ticket_info: {
    disclaimer: string,
    sms_number: string,
    tickets: {
      description: string,
      name: string,
      price: string,
      sms: string
    }
  };
}
```
