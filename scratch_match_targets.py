import pandas as pd

events = pd.read_csv('dataset/financial_events.csv')
for uid, target in [('user_08', 452.00), ('user_22', 114.00), ('user_18', 624.00), ('user_15', 487.00), ('user_21', 515.00), ('user_06', 539.10)]:
    ue = events[(events['user_id'] == uid) & (events['direction'] == 'debit') & (events['status'] == 'settled')].copy()
    ue['ym'] = ue['event_date'].str[:7]
    print(f"*** {uid} target={target} ***")
    for ym, grp in ue.groupby('ym'):
        s = grp['amount'].sum()
        print(f"  {ym}: total_sum={s:.2f}")
        # check without rent/housing
        non_housing = grp[~grp['category'].isin(['rent', 'housing'])]['amount'].sum()
        print(f"  {ym}: non_housing_sum={non_housing:.2f}")
