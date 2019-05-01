package com.grysz

import arrow.core.Option

class App {
  val greeting = Option.just("Hello world.")
}

fun main() {
  println(App().greeting)
}
