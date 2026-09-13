import pandas as pd
import numpy as np

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

def simulate_user(req_id):
    sr = samples[samples['request_id'] == req_id].iloc[0]
    uid = sr['user_id']
    req_date = pd.to_datetime(sr['request_date'])
    p = profiles[profiles['user_id'] == uid].iloc[0]
    start_bal = float(p['current_available_balance'])
    min_keep = float(p['minimum_balance_to_keep'])
    req_amt = float(sr['requested_amount'])
    safe_sample = float(sr['amount_safe_to_pay'])
    
    u_events = events[events['user_id'] == uid].copy()
    u_events['ed'] = pd.to_datetime(u_events['event_date'])
    
    # 1. Past settled debits to detect cadence
    past_debits = u_events[(u_events['ed'] < req_date) & (u_events['direction'] == 'debit') & (u_events['status'] == 'settled')]
    
    streams = {}
    for cat, group in past_debits.groupby('category'):
        group = group.sort_values('ed')
        dates = group['ed'].tolist()
        amounts = group['amount'].tolist()
        descs = group['description'].tolist()
        event_ids = group['event_id'].tolist()
        flex = group['flexibility'].tolist()
        min_allowed = group['minimum_allowed_amount'].tolist()
        
        if len(dates) >= 2:
            intervals = [(dates[i+1] - dates[i]).days for i in range(len(dates)-1)]
            median_int = int(round(np.median(intervals)))
            last_date = dates[-1]
            last_amt = amounts[-1]
            last_desc = descs[-1]
            last_eid = event_ids[-1]
            last_flex = flex[-1]
            last_min_allowed = min_allowed[-1]
            
            # Cadence type
            if 27 <= median_int <= 32:
                cadence = ('monthly', last_date.day)
            elif 85 <= median_int <= 95:
                cadence = ('quarterly', last_date.day)
            else:
                cadence = ('interval', median_int)
                
            streams[cat] = {
                'cadence': cadence,
                'last_date': last_date,
                'amount': last_amt,
                'desc': last_desc,
                'event_id': last_eid,
                'flexibility': last_flex,
                'min_allowed': last_min_allowed
            }
    
    # Project 90 days from req_date
    horizon_end = req_date + pd.Timedelta(days=90)
    
    # Check pending debits
    pending = u_events[(u_events['status'] == 'pending') & (u_events['direction'] == 'debit')]
    
    # Check scheduled income/salary
    salaries = u_events[(u_events['direction'] == 'credit') & (u_events['category'] == 'salary')].sort_values('ed')
    last_sal = salaries.iloc[-1]
    sal_amt = float(last_sal['amount'])
    sal_day = last_sal['ed'].day
    
    print(f"\n=================== {req_id} ({uid}) ===================")
    print(f"start_bal={start_bal}, min_keep={min_keep}, req_amt={req_amt}, safe_sample={safe_sample}")
    print(f"Expected changes: {sr['spending_changes_needed']}")
    
    # Project calendar day by day
    # Baseline simulation (no proposed payments)
    def run_sim(stopped_ids=set(), reduced_map={}, plan_payments={}):
        bal = start_bal
        min_bal_seen = bal
        min_bal_date = req_date
        
        # Build event schedule
        events_by_date = {}
        def add_event(d, amt, direction, name, eid):
            if d < req_date or d > horizon_end:
                return
            if eid in stopped_ids:
                return
            if eid in reduced_map:
                amt = reduced_map[eid]
            events_by_date.setdefault(d, []).append((amt, direction, name, eid))
            
        # Add pending debits on req_date or settlement date
        for _, pe in pending.iterrows():
            d = max(req_date, pe['ed'])
            add_event(d, float(pe['amount']), 'debit', pe['description'], pe['event_id'])
            
        # Add future scheduled events
        future = u_events[(u_events['ed'] >= req_date) & (u_events['status'] != 'pending') & (u_events['status'] != 'cancelled')]
        future_cats_by_month = set()
        for _, fe in future.iterrows():
            d = fe['ed']
            add_event(d, float(fe['amount']), fe['direction'], fe['description'], fe['event_id'])
            future_cats_by_month.add((fe['category'], d.year, d.month))
            
        # Project salary monthly if not present
        cur_m = req_date.to_period('M')
        end_m = horizon_end.to_period('M')
        while cur_m <= end_m:
            if ('salary', cur_m.year, cur_m.month) not in future_cats_by_month:
                day = min(sal_day, cur_m.days_in_month)
                d = pd.to_datetime(f"{cur_m.year}-{cur_m.month:02d}-{day:02d}")
                if d >= req_date and d <= horizon_end:
                    add_event(d, sal_amt, 'credit', 'Projected salary', f"proj_sal_{cur_m}")
            cur_m += 1
            
        # Project recurring streams
        for cat, s in streams.items():
            cadence_type, val = s['cadence']
            amt = float(s['amount'])
            eid = s['event_id']
            name = s['desc']
            
            if cadence_type == 'monthly':
                cur_m = req_date.to_period('M')
                while cur_m <= end_m:
                    # check if already occurred in cur_m prior to req_date or scheduled
                    # If this category had an event in cur_m, don't double project
                    past_in_month = past_debits[(past_debits['category'] == cat) & (past_debits['ed'].dt.to_period('M') == cur_m)]
                    if len(past_in_month) == 0 and (cat, cur_m.year, cur_m.month) not in future_cats_by_month:
                        day = min(val, cur_m.days_in_month)
                        d = pd.to_datetime(f"{cur_m.year}-{cur_m.month:02d}-{day:02d}")
                        if d >= req_date and d <= horizon_end:
                            add_event(d, amt, 'debit', name, eid)
                    cur_m += 1
            elif cadence_type == 'interval':
                step = val
                next_d = s['last_date'] + pd.Timedelta(days=step)
                while next_d <= horizon_end:
                    if next_d >= req_date:
                        add_event(next_d, amt, 'debit', name, eid)
                    next_d += pd.Timedelta(days=step)
                    
        # Simulate day by day
        cur_d = req_date
        while cur_d <= horizon_end:
            if cur_d in events_by_date:
                for amt, direction, name, eid in events_by_date[cur_d]:
                    if direction == 'credit':
                        bal += amt
                    else:
                        bal -= amt
            if cur_d in plan_payments:
                bal -= plan_payments[cur_d]
            if bal < min_bal_seen:
                min_bal_seen = bal
                min_bal_date = cur_d
            cur_d += pd.Timedelta(days=1)
            
        return min_bal_seen, min_bal_date
        
    base_min, base_date = run_sim()
    print(f"Baseline min balance: {base_min:.2f} on {base_date.strftime('%Y-%m-%d')}")
    safe_today = max(0.0, base_min - min_keep)
    print(f"Calculated safe today: {safe_today:.2f} (Sample was: {safe_sample:.2f})")
    
    # Try full payment with expected changes
    changes_str = sr['spending_changes_needed']
    stopped = set()
    reduced = {}
    if changes_str != 'none':
        for c in changes_str.split('|'):
            if c.startswith('stop:'):
                stopped.add(c.split(':')[1])
            elif c.startswith('reduce_to:'):
                parts = c.split(':')
                reduced[parts[1]] = float(parts[2])
                
    plan_full = {req_date: req_amt}
    min_with_changes, min_d = run_sim(stopped_ids=stopped, reduced_map=reduced, plan_payments=plan_full)
    print(f"With changes & full payment: min balance: {min_with_changes:.2f} (min_keep={min_keep}) -> SAFE={min_with_changes >= min_keep}")

for r in ['request_06', 'request_11', 'request_21']:
    simulate_user(r)
