import pandas as pd
import numpy as np

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

def detect_patterns(uid, req_date):
    u_events = events[events['user_id'] == uid].copy()
    u_events['ed'] = pd.to_datetime(u_events['event_date'])
    past = u_events[(u_events['ed'] <= req_date) & (u_events['status'] == 'settled')]
    
    # 1. Salary patterns
    salaries = past[(past['category'] == 'salary') & (past['direction'] == 'credit')]
    sal_patterns = []
    for desc, sgroup in salaries.groupby('description'):
        sgroup = sgroup.sort_values('ed')
        dates = sgroup['ed'].tolist()
        amts = sgroup['amount'].tolist()
        if len(dates) >= 2:
            intervals = [(dates[i+1] - dates[i]).days for i in range(len(dates)-1)]
            med = int(round(np.median(intervals)))
            if 6 <= med <= 8:
                sal_patterns.append(('interval', 7, amts[-1], dates[-1], 'salary', desc))
            elif 12 <= med <= 16:
                sal_patterns.append(('interval', 14, amts[-1], dates[-1], 'salary', desc))
            elif 27 <= med <= 33:
                sal_patterns.append(('monthly', dates[-1].day, amts[-1], dates[-1], 'salary', desc))
        elif len(dates) == 1:
            sal_patterns.append(('monthly', dates[0].day, amts[0], dates[0], 'salary', desc))
            
    # 2. Debit patterns
    debit_patterns = []
    debits = past[past['direction'] == 'debit']
    for cat, dgroup in debits.groupby('category'):
        dgroup = dgroup.sort_values('ed')
        dates = dgroup['ed'].tolist()
        amts = dgroup['amount'].tolist()
        eids = dgroup['event_id'].tolist()
        flex = dgroup['flexibility'].tolist()
        min_amt = dgroup['minimum_allowed_amount'].tolist()
        descs = dgroup['description'].tolist()
        
        if len(dates) >= 2:
            intervals = [(dates[i+1] - dates[i]).days for i in range(len(dates)-1)]
            med = int(round(np.median(intervals)))
            if 4 <= med <= 6:
                debit_patterns.append(('interval', med, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
            elif 6 <= med <= 8:
                debit_patterns.append(('interval', 7, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
            elif 9 <= med <= 11:
                debit_patterns.append(('interval', 10, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
            elif 12 <= med <= 16:
                debit_patterns.append(('interval', 14, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
            elif 19 <= med <= 23:
                debit_patterns.append(('interval', 21, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
            elif 27 <= med <= 33:
                debit_patterns.append(('monthly', dates[-1].day, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
            elif 80 <= med <= 100:
                debit_patterns.append(('quarterly', dates[-1].day, amts[-1], dates[-1], cat, descs[-1], eids[-1], flex[-1], min_amt[-1]))
        elif len(dates) == 1:
            desc_l = descs[0].lower()
            if any(k in desc_l for k in ['rent', 'subscription', 'monthly', 'insurance', 'utilities', 'fee', 'plan']):
                debit_patterns.append(('monthly', dates[0].day, amts[0], dates[0], cat, descs[0], eids[0], flex[0], min_amt[0]))
                
    return sal_patterns, debit_patterns

def simulate(req_id):
    sr = samples[samples['request_id'] == req_id].iloc[0]
    uid = sr['user_id']
    req_date = pd.to_datetime(sr['request_date'])
    horizon_end = req_date + pd.Timedelta(days=90)
    p = profiles[profiles['user_id'] == uid].iloc[0]
    start_bal = float(p['current_available_balance'])
    min_keep = float(p['minimum_balance_to_keep'])
    
    sal_pats, deb_pats = detect_patterns(uid, req_date)
    
    u_events = events[events['user_id'] == uid].copy()
    u_events['ed'] = pd.to_datetime(u_events['event_date'])
    
    # Run sim
    def run_sim(stopped_ids=set(), reduced_map={}, plan_payments={}):
        events_by_date = {}
        def add_ev(d, amt, direction, eid):
            if d < req_date or d > horizon_end:
                return
            if eid in stopped_ids:
                return
            if eid in reduced_map:
                amt = reduced_map[eid]
            events_by_date.setdefault(d, []).append((amt, direction))
            
        # Pending debits
        pending = u_events[(u_events['status'] == 'pending') & (u_events['direction'] == 'debit')]
        for _, pe in pending.iterrows():
            d = max(req_date, pe['ed'])
            add_ev(d, float(pe['amount']), 'debit', pe['event_id'])
            
        # Future events
        future = u_events[(u_events['ed'] >= req_date) & (u_events['status'] != 'pending') & (u_events['status'] != 'cancelled')]
        future_cats_by_month = set()
        for _, fe in future.iterrows():
            d = fe['ed']
            add_ev(d, float(fe['amount']), fe['direction'], fe['event_id'])
            future_cats_by_month.add((fe['category'], d.year, d.month))
            
        # Project salaries
        end_m = horizon_end.to_period('M')
        for spat in sal_pats:
            ptype = spat[0]
            if ptype == 'monthly':
                day = spat[1]
                amt = spat[2]
                cur_m = req_date.to_period('M')
                while cur_m <= end_m:
                    if ('salary', cur_m.year, cur_m.month) not in future_cats_by_month:
                        vday = min(day, cur_m.days_in_month)
                        d = pd.to_datetime(f"{cur_m.year}-{cur_m.month:02d}-{vday:02d}")
                        if cur_m == req_date.to_period('M'):
                            if d >= req_date and d <= horizon_end:
                                add_ev(d, amt, 'credit', f"proj_sal_{cur_m}_{vday}")
                        else:
                            if req_date <= d <= horizon_end:
                                add_ev(d, amt, 'credit', f"proj_sal_{cur_m}_{vday}")
                    cur_m += 1
            elif ptype == 'interval':
                step = spat[1]
                amt = spat[2]
                d = spat[3] + pd.Timedelta(days=step)
                while d <= horizon_end:
                    if d >= req_date:
                        add_ev(d, amt, 'credit', f"proj_sal_{d}")
                    d += pd.Timedelta(days=step)
                    
        # Project debits
        for dpat in deb_pats:
            ptype = dpat[0]
            if ptype == 'monthly':
                day = dpat[1]
                amt = dpat[2]
                cat = dpat[4]
                eid = dpat[6]
                cur_m = req_date.to_period('M')
                while cur_m <= end_m:
                    # check if already occurred in cur_m on or before req_date
                    past_in_m = u_events[(u_events['ed'] <= req_date) & (u_events['category'] == cat) & (u_events['ed'].dt.to_period('M') == cur_m)]
                    # If in request month, only project if day > req_date.day and no past event in month
                    if cur_m == req_date.to_period('M'):
                        if day > req_date.day and len(past_in_m) == 0 and (cat, cur_m.year, cur_m.month) not in future_cats_by_month:
                            vday = min(day, cur_m.days_in_month)
                            d = pd.to_datetime(f"{cur_m.year}-{cur_m.month:02d}-{vday:02d}")
                            if req_date <= d <= horizon_end:
                                add_ev(d, amt, 'debit', eid)
                    else:
                        if (cat, cur_m.year, cur_m.month) not in future_cats_by_month:
                            vday = min(day, cur_m.days_in_month)
                            d = pd.to_datetime(f"{cur_m.year}-{cur_m.month:02d}-{vday:02d}")
                            if req_date <= d <= horizon_end:
                                add_ev(d, amt, 'debit', eid)
                    cur_m += 1
            elif ptype == 'interval':
                step = dpat[1]
                amt = dpat[2]
                eid = dpat[6]
                d = dpat[3] + pd.Timedelta(days=step)
                while d <= horizon_end:
                    if d > req_date:
                        add_ev(d, amt, 'debit', eid)
                    d += pd.Timedelta(days=step)
                    
        # Day by day simulation
        bal = start_bal
        min_bal = bal
        min_d = req_date
        cur_d = req_date
        while cur_d <= horizon_end:
            if cur_d in events_by_date:
                for amt, direction in events_by_date[cur_d]:
                    if direction == 'credit':
                        bal += amt
                    else:
                        bal -= amt
            if cur_d in plan_payments:
                bal -= plan_payments[cur_d]
            if bal < min_bal:
                min_bal = bal
                min_d = cur_d
            cur_d += pd.Timedelta(days=1)
        return min_bal, min_d
        
    base_min, base_d = run_sim()
    safe = max(0.0, base_min - min_keep)
    print(f"{req_id} ({uid}): Baseline min={base_min:.2f} on {base_d.strftime('%Y-%m-%d')}, safe={safe:.2f}, sample_safe={sr['amount_safe_to_pay']:.2f}")
    
    # Check with changes
    changes = sr['spending_changes_needed']
    stopped = set()
    reduced = {}
    if changes != 'none':
        for c in changes.split('|'):
            if c.startswith('stop:'):
                stopped.add(c.split(':')[1])
            elif c.startswith('reduce_to:'):
                parts = c.split(':')
                reduced[parts[1]] = float(parts[2])
    plan = {req_date: float(sr['requested_amount'])}
    min_c, min_cd = run_sim(stopped_ids=stopped, reduced_map=reduced, plan_payments=plan)
    print(f"  With changes & full payment: min={min_c:.2f} (min_keep={min_keep:.2f}) -> SAFE={min_c >= min_keep}")

for r in ['request_06', 'request_11', 'request_21']:
    simulate(r)
