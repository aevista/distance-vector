package com.network.control

object Control {
  def apply[A](): Control[A ,A] =
    new Control[A, A](a => (cb: A => Unit) => cb(a))

  def process[A, V](a: => A): Control[A, V] =
    new Control[A, V](_ => (cb: A => Unit) => cb(a))
}

class Control[A, V] private(private val control: V => (A => Unit) => Unit) {

  final def map[B](f: A => B): Control[B, V] =
    flatMap(a => Control.process(f(a)))

  final def flatMap[B](f: A => Control[B, V]): Control[B, V] =
    new Control[B, V](v => cb => control(v){a => f(a).control(v)(cb)})

  final def filter(f: A => Boolean): Control[A, V] =
    new Control[A, V](v => cb => control(v){a => if (f(a)) cb(a)})

  final def andThen[B](f: A => B): Control[A, V] =
    map(a => { f(a); a })

  final def process(v: V): Unit =
    control(v)(identity[A])

  override final def toString: String = "Control()"

}

