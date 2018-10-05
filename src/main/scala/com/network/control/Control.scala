package com.network.control

object Control {
  def apply[A](): Control[A,A] =
    new Control[A, A](a => (cb: A => Unit) => cb(a))

  def process[A, Any](a: => A): Control[A, Any] =
    new Control[A, Any](_ => (cb: A => Unit) => cb(a))
}

class Control[A, Any] private(private val control: Any => (A => Unit) => Unit) {

  def map[B](f: A => B): Control[B, Any] =
    flatMap(a => Control.process(f(a)))

  def flatMap[B](f: A => Control[B, Any]): Control[B, Any] =
    new Control[B,Any](any => cb => control(any){a => f(a).control(any)(cb)})

  def filter(f: A => Boolean): Control[A, Any] =
    new Control[A,Any](ack => cb => control(ack){a => Control.process(a).control(ack){a => if (f(a)) cb(a)}})

  def andThen[B](f: A => B): Control[A, Any] =
    map(a => {f(a); a})

  def process(any: Any): Unit =
    control(any)(identity[A])

}

