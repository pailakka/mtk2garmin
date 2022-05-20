import sqlite3
import sys
import csv

con = sqlite3.connect(f'file:{sys.argv[1]}?mode=ro', uri=True)

cur = con.cursor()
cur.execute("select table_name from gpkg_contents where data_type = 'features'")
feature_tables = cur.fetchall()

with open('kohdeluokka_map.csv', 'w+', newline='') as csvfile:
    writer = csv.writer(csvfile, delimiter=';')
    for (lname,) in feature_tables:
        print(lname, end=" ")
        cur.execute(f'SELECT kohdeluokka,COUNT(*) FROM {lname} GROUP BY kohdeluokka')
        classes = cur.fetchall()
        tot = 0
        for cid, cnum in classes:
            writer.writerow((lname, cid, cnum))
            tot += cnum
        print(tot)
