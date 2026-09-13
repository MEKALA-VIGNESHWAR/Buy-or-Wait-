import pandas as pd
from datetime import timedelta

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')

def simulate_90_days(uid, req_date_str, proposed_payments=[], stop_ids=[], reduce_map={}):
    p = profiles[profiles['user_id'] == uid].iloc[0]
    req_date = pd.to_datetime(req_date_str)
    horizon_end = req_date + timedelta(days=90)
    
    # Starting balance
    bal = p['current_available_balance']
    min_keep = p['minimum_balance_to_keep']
    
    # Baseline month: latest settled month before req_date
    past = events[(events['user_id'] == uid) & (events['direction'] == 'debit') & (events['status'] == 'settled') & (pd.to_datetime(events['event_date']) < req_date)]
    latest_month = past['event_date'].max()[:7]
    month_events = past[past['event_date'].str.startswith(latest_month)]
    
    # Salary
    salaries = events[(events['user_id'] == uid) & (events['direction'] == 'credit') & (events['category'] == 'salary')]
    last_sal = salaries.sort_values('event_date').iloc[-1]
    sal_day = pd.to_datetime(last_sal['event_date']).day
    sal_amt = last_sal['amount']
    
    # Build schedule of events from req_date to horizon_end
    daily_flows = {}
    
    # Recurring salary
    cur = req_date
    while cur <= horizon_end:
        # salary day in this month
        import calendar
        max_day = calendar.monthrange(cur.year, cur.month)[1]
        valid_day = min(sal_day, max_day)
        sal_date = pd.Timestamp(year=cur.year, month=cur.month, day=valid_day)
        if sal_date >= req_date and sal_date <= horizon_end:
            daily_flows[sal_date] = daily_flows.get(sal_date, 0.0) + sal_amt
        # advance month
        if cur.month == 12:
            cur = pd.Timestamp(year=cur.year+1, month=1, day=1)
        else:
            cur = pd.Timestamp(year=cur.year, month=cur.month+1, day=1)
            
    # Recurring debits
    cur = req_date
    while cur <= horizon_end:
        for idx, ev in month_events.iterrows():
            eid = ev['event_id']
            if eid in stop_ids:
                continue
            amt = ev['amount']
            if eid in reduce_map:
                amt = reduce_map[eid]
            ev_day = pd.to_datetime(ev['event_date']).day
            import calendar
            max_day = calendar.monthrange(cur.year, cur.month)[1]
            valid_day = min(ev_day, max_day)
            ev_date = pd.Timestamp(year=cur.year, month=cur.month, day=valid_day)
            if ev_date > req_date and ev_date <= horizon_end:
                daily_flows[ev_date] = daily_flows.get(ev_date, 0.0) - amt
        # advance month
        if cur.month == 12:
            cur = pd.Timestamp(year=cur.year+1, month=1, day=1)
        else:
            cur = pd.Timestamp(year=cur.year, month=cur.month+1, day=1)
            
    # Apply proposed payments
    for p_date, p_amt in proposed_payments:
        daily_flows[p_date] = daily_flows.get(p_date, 0.0) - p_amt
        
    # Day-by-day simulation
    cur = req_date
    min_obs = bal
    min_date = req_date
    
    # If payment on req_date
    for p_date, p_amt in proposed_payments:
        if p_date == req_date:
            bal -= p_amt
            if bal < min_obs:
                min_obs = bal
                min_date = cur
                
    while cur <= horizon_end:
        if cur in daily_flows and cur > req_date:
            bal += daily_flows[cur]
            if bal < min_obs:
                min_obs = bal
                min_date = cur
        cur += timedelta(days=1)
        
    print(f"Simulation {uid}: start={p['current_available_balance']}, min_obs={min_obs:.2f} on {min_date.strftime('%Y-%m-%d')}, safe={min_obs >= min_keep}")
    return min_obs, min_obs >= min_keep

print("--- request_06 baseline without proposed payment ---")
simulate_90_days('user_06', '2026-01-03', [])
print("--- request_06 full payment 620.40 without spending changes ---")
simulate_90_days('user_06', '2026-01-03', [(pd.to_datetime('2026-01-03'), 620.40)])
print("--- request_06 full payment 620.40 WITH stop:event_476 ---")
simulate_90_days('user_06', '2026-01-03', [(pd.to_datetime('2026-01-03'), 620.40)], stop_ids=['event_476'])
