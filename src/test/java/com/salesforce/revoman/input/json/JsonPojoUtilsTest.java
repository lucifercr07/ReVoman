/***************************************************************************************************
 *  Copyright (c) 2023, Salesforce, Inc. All rights reserved. SPDX-License-Identifier:
 *           Apache License Version 2.0
 *  For full license text, see the LICENSE file in the repo root or
 *  http://www.apache.org/licenses/LICENSE-2.0
 **************************************************************************************************/

package com.salesforce.revoman.input.json;

import static com.google.common.truth.Truth.assertThat;

import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonPojoUtilsTest {

  @Test
  @DisplayName("json file To Pojo")
  void jsonFileToPojo() {
    final var nestedBeanFromJson =
        JsonPojoUtils.<NestedBean>jsonFileToPojo(NestedBean.class, "json/nested-bean.json");
    assertThat(nestedBeanFromJson).isNotNull();
    assertThat(nestedBeanFromJson.getName()).isEqualTo("container");
    assertThat(nestedBeanFromJson.getBean().getItems()).hasSize(2);
  }

  @Test
  @DisplayName("json with Epoch Date To Pojo")
  void jsonWithEpochDateToPojo() {
    final var beanWithDate =
        JsonPojoUtils.<BeanWithDate>jsonToPojo(BeanWithDate.class, "{\"date\": 1604216172813}");
    assertThat(beanWithDate).isNotNull();
    assertThat(beanWithDate.date).isNotNull();
  }

  @Test
  @DisplayName("json with ISO Date To Pojo")
  void jsonWithISODateToPojo() {
    final var beanWithDate =
        JsonPojoUtils.<BeanWithDate>jsonToPojo(BeanWithDate.class, "{\"date\": \"2015-09-01\"}");
    assertThat(beanWithDate).isNotNull();
    assertThat(beanWithDate.date).isNotNull();
  }

  @Test
  @DisplayName("pojo to json")
  void pojoToJson() {
    final var nestedBean = new NestedBean("container", new Bean("bean", List.of("item1", "item2")));
    final var nestedBeanJson = JsonPojoUtils.pojoToJson(NestedBean.class, nestedBean);
    System.out.println(nestedBeanJson);
    assertThat(nestedBeanJson).isNotEmpty();
  }

  private static class Bean {
    private final String name;
    private final List<String> items;

    private Bean(String name, List<String> items) {
      this.name = name;
      this.items = items;
    }

    public String getName() {
      return name;
    }

    public List<String> getItems() {
      return items;
    }
  }

  private static class NestedBean {
    private final String name;
    private final Bean bean;

    private NestedBean(String name, Bean bean) {
      this.name = name;
      this.bean = bean;
    }

    public String getName() {
      return name;
    }

    public Bean getBean() {
      return bean;
    }
  }

  private static class BeanWithDate {
    private final Date date;

    private BeanWithDate(Date date) {
      this.date = date;
    }

    public Date getDate() {
      return date;
    }

    @Override
    public String toString() {
      return "BeanWithDate{" + "date=" + date + '}';
    }
  }
}
