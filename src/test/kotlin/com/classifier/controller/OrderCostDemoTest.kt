package com.classifier.controller

import com.classifier.service.OrderInitializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal

@SpringBootTest(properties=["classifier.demo.enabled=true","classifier.bootstrap.enabled=true"])
@Testcontainers
class OrderCostDemoTest {
    companion object {
        @Container val postgres=PostgreSQLContainer("postgres:15")
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url",postgres::getJdbcUrl)
            r.add("spring.datasource.username",postgres::getUsername)
            r.add("spring.datasource.password",postgres::getPassword)
        }
    }
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var initializer: OrderInitializer
    @Test fun `all shop variants and orders have component based costs`() {
        assertEquals(18,jdbc.queryForObject("SELECT count(*) FROM product_variant WHERE code LIKE 'SHOP-%'",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM product_variant v CROSS JOIN LATERAL orders_item_cost(NULL,v.id) c WHERE v.code LIKE 'SHOP-%' AND (c.unit_price IS NULL OR c.unit_price<=0 OR c.components='[]'::jsonb)",Int::class.java))
        assertEquals(8,jdbc.queryForObject("SELECT count(*) FROM orders_amounts a JOIN customer_order o ON o.id=a.order_id WHERE o.order_number LIKE 'SHOP-%' AND a.unpriced_line_count=0 AND a.total_amount::numeric>0",Int::class.java))
    }
    @Test fun `resource price changes propagate and initializer preserves component setup`() {
        val sql="SELECT c.unit_price FROM product_variant v CROSS JOIN LATERAL orders_item_cost(NULL,v.id) c WHERE v.code='SHOP-PC-GAMING'"
        val before=jdbc.queryForObject(sql,BigDecimal::class.java)!!
        val rows=jdbc.queryForObject("SELECT count(*) FROM configuration_component",Int::class.java)
        jdbc.update("UPDATE node_numeric_value SET value=value+1 WHERE classifier_node_id=(SELECT id FROM classifier_node WHERE code='BOM-EL-PC-CPU') AND numeric_parameter_id=(SELECT id FROM numeric_parameter WHERE code='PURCHASE_PRICE')")
        initializer.run(DefaultApplicationArguments())
        assertEquals(before+BigDecimal.ONE,jdbc.queryForObject(sql,BigDecimal::class.java))
        assertEquals(rows,jdbc.queryForObject("SELECT count(*) FROM configuration_component",Int::class.java))
    }
}
