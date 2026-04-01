import json
import pandas as pd
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
src = ROOT / "data" / "raw" / "real" / "hospital_data_sampleee.xlsx"
out_dir = ROOT / "data" / "processed" / "real"
out_dir.mkdir(parents=True, exist_ok=True)

df = pd.read_excel(src)
df.columns = [str(c).strip() for c in df.columns]

def combine_dt(date, time):
    if pd.isna(date) or pd.isna(time):
        return pd.NaT
    if isinstance(time, pd.Timestamp):
        time = time.time()
    return pd.Timestamp.combine(pd.to_datetime(date).date(), time)

df["entry_dt"] = df.apply(lambda r: combine_dt(r["Date"], r["Entry Time"]), axis=1)
df["post_dt"] = df.apply(lambda r: combine_dt(r["Date"], r["Post-Consultation Time"]), axis=1)
df["completion_dt"] = df.apply(lambda r: combine_dt(r["Date"], r["Completion Time"]), axis=1)
df["entry_hour"] = df["entry_dt"].dt.hour
df["entry_date"] = df["entry_dt"].dt.date
df["wait_minutes"] = (df["post_dt"] - df["entry_dt"]).dt.total_seconds() / 60
df["system_minutes"] = (df["completion_dt"] - df["entry_dt"]).dt.total_seconds() / 60

clean_cols = ["Date", "Doctor Type", "Financial Class", "Patient Type", "Patient ID",
              "entry_dt", "post_dt", "completion_dt", "entry_hour", "wait_minutes", "system_minutes"]
df[clean_cols].to_csv(out_dir / "hospital_wait_sample_cleaned.csv", index=False)

hourly = (df.groupby("entry_hour").size()
            .reindex(range(24), fill_value=0)
            .rename("arrival_count")
            .reset_index()
            .rename(columns={"entry_hour": "hour"}))
hourly["arrival_share"] = hourly["arrival_count"] / hourly["arrival_count"].sum()
hourly.to_csv(out_dir / "hourly_arrival_profile_from_sample.csv", index=False)

daily_hourly = (
    df.dropna(subset=["entry_dt"])
    .groupby(["entry_date", "entry_hour"])
    .size()
    .reset_index(name="arrival_count")
    .sort_values(["entry_date", "entry_hour"])
)
daily_hourly.to_csv(out_dir / "daily_hourly_arrivals_from_sample.csv", index=False)

summary = {
    "rows": int(len(df)),
    "avg_wait_minutes": float(df["wait_minutes"].mean()),
    "avg_system_minutes": float(df["system_minutes"].mean())
}
with open(out_dir / "hospital_wait_sample_summary.json", "w") as f:
    json.dump(summary, f, indent=2)

print(
    "Wrote processed files to",
    out_dir,
    "(cleaned CSV, hourly profile, daily×hourly CSV, summary JSON)",
)
