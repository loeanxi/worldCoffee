/**
 * 给 coffee_order 表加 coupon_id 和 discount_amount 两列。
 * 运行方式：cd backend && node add-coupon-columns.js
 */
const mysql = require('mysql2/promise')

async function main() {
  const conn = await mysql.createConnection({
    host: 'localhost',
    port: 3306,
    user: 'root',
    password: '123456',
    database: 'worldcoffee'
  })

  // 检查列是否已存在
  const [cols] = await conn.query(
    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='worldcoffee' AND TABLE_NAME='coffee_order' AND COLUMN_NAME IN ('coupon_id','discount_amount')"
  )
  if (cols.length >= 2) {
    console.log('列已存在，无需重复执行')
    await conn.end()
    return
  }

  // 加列
  if (cols.find(c => c.COLUMN_NAME === 'coupon_id') === undefined) {
    await conn.query('ALTER TABLE coffee_order ADD COLUMN coupon_id BIGINT NULL COMMENT "使用的优惠券ID"')
    console.log('+ coupon_id 列已添加')
  }
  if (cols.find(c => c.COLUMN_NAME === 'discount_amount') === undefined) {
    await conn.query('ALTER TABLE coffee_order ADD COLUMN discount_amount DECIMAL(10,2) NULL DEFAULT 0 COMMENT "优惠金额"')
    console.log('+ discount_amount 列已添加')
  }

  console.log('完成！')
  await conn.end()
}

main().catch(e => { console.error(e); process.exit(1) })
