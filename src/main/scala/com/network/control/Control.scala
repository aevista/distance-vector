package com.network.control

object Control {
  def apply[A](): Control[A, A] =
    new Control[A, A](a => (cb: A => Unit) => cb(a))

  def process[V, A](a: => A): Control[V, A] =
    new Control[V, A](_ => (cb: A => Unit) => cb(a))
}

class Control[V, A] private(private val control: V => (A => Unit) => Unit) {

  final def map[B](f: A => B): Control[V, B] =
    flatMap(a => Control.process(f(a)))

  final def flatMap[B](f: A => Control[V, B]): Control[V, B] =
    new Control[V, B](v => cb => control(v){a => f(a).control(v)(cb)})

  final def filter(f: A => Boolean): Control[V, A] =
    new Control[V, A](v => cb => control(v){a => if (f(a)) cb(a)})

  final def andThen[B](f: A => B): Control[V, A] =
    map(a => { f(a); a })

  final def process(v: V): Unit =
    control(v)(identity[A])

  override final def toString: String = "Control()"

}

