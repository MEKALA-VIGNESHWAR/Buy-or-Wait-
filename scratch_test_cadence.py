import pandas as pd
import numpy as np

events = pd.read_csv('dataset/financial_events.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
samples = pd.read_csv('dataset/sample_requests.csv')

def analyze_user(uid, req_date_str):
    req_date = pd.to_datetime(req_date_str)
    u_events = events[events['user_id'] == uid].copy()
    u_events['ed'] = pd.to_datetime(u_events['event_date'])
    past_debits = u_events[(u_events['ed'] < req_date) & (u_events['direction'] == 'debit') & (u_events['status'] == 'settled')]
    
    print(f"\n=================== {uid} (before {req_date_str}) ===================")
    for cat, group in past_debits.groupby('category'):
        group = group.sort_values('ed')
        dates = group['ed'].tolist()
        amounts = group['amount'].tolist()
        descs = group['description'].tolist()
        if len(dates) >= 2:
            intervals = [(dates[i+1] - dates[i]).days for i in range(len(dates)-1)]
            median_interval = np.median(intervals)
            print(f"Category '{cat}': count={len(dates)}, median_interval={median_interval:.1f}d, intervals={intervals[-4:]}, last_date={dates[-1].strftime('%Y-%m-%d')}, last_amt={amounts[-1]}, desc='{descs[-1]}'")
        else:
            print(f"Category '{cat}': count=1, date={dates[0].strftime('%Y-%m-%d')}, amt={amounts[0]}, desc='{descs[0]}'")

analyze_user('user_06', '2026-01-03')
analyze_user('user_11', '2025-05-03')
analyze_user('user_21', '2026-04-03')
