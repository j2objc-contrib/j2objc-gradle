

import UIKit

class ViewController: UIViewController {

  override func viewDidLoad() {
    super.viewDidLoad()
    
    // call a method from a class of the shared project
    let a: Int = 5
    let b: Int = 10
    
    var result = ComExampleSharedClass.addWithInt(5, withInt: 10)
    println("Result: \(result)")
  }
  

}

